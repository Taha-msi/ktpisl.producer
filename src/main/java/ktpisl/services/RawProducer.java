package ktpisl.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import ktpisl.dao.MongoDataDao;
import ktpisl.dao.RT2DaoImpl;
import ktpisl.dao.SensorDataDao;
import ktpisl.dao.SensorDataDaoImpl;
import ktpisl.models.SensorDataBean;
import ktpisl.utils.Date;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import static java.lang.Thread.*;

public class RawProducer {
    final static Logger logger = LogManager.getLogger(RawProducer.class);

    public RawProducer() {
    }

    public static void main(String[] args) {
        new RawProducer().run();
    }

    private void run() {

        // Use Jackson instead of GSon
        // implement a delete tread

        logger.info("Start over!!!");
        CountDownLatch latch = new CountDownLatch(2);
        ExecutorService executor = Executors.newWorkStealingPool();
        List<ProducerCallable> callables = new ArrayList<>();
        callables.add(new ProducerCallable(latch, "sensorData"));
        callables.add(new ProducerCallable(latch, "rt2"));
        List<String> times = new ArrayList<>();
        try {
            executor.invokeAll(callables)
                    .stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .forEach(a -> times.add(a));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("stopping application ...");
            logger.info("shutting down client ...");
            logger.info("closing producer ...");
            callables.forEach(a -> a.shutdown());
            logger.info("Done!");
        }));
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.info("Application got interrupted ", e);
        }
    }

    public class ProducerCallable implements Callable<String> {
        private CountDownLatch latch;
        private String fetchTime;
        private KafkaProducer<String, String> producer;
        private SensorDataDao sensorDataDao;
        private MongoDataDao mongoDataDao;
        private String topic = "sensor_data";
        private Gson gson;

        public ProducerCallable(CountDownLatch latch, String schemaName) {
            if ("rt2".equals(schemaName))
                this.sensorDataDao = new RT2DaoImpl();
            else
                this.sensorDataDao = new SensorDataDaoImpl();
            this.producer = createKafkaProducer();
            this.mongoDataDao = new MongoDataDao();
            GsonBuilder builder = new GsonBuilder();
            builder.setPrettyPrinting().serializeNulls();
            gson = builder.create();
            this.latch = latch;
        }

        public void shutdown() {
            producer.close();
        }

        private KafkaProducer<String, String> createKafkaProducer() {
            Properties properties = new Properties();
            properties.setProperty(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "178.62.86.223:9092");
            properties.setProperty(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            properties.setProperty(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            properties.setProperty(ProducerConfig.ACKS_CONFIG, "all");
            properties.setProperty(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
            properties.setProperty(ProducerConfig.RETRIES_CONFIG, Integer.toString(Integer.MAX_VALUE));
            properties.setProperty(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "5");
            properties.setProperty(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
            properties.setProperty(ProducerConfig.LINGER_MS_CONFIG, "20");// in order to specify the wait time to reach to a batch size
            properties.setProperty(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32 * 1024)); // the maximum of a batch size
            //properties.setProperty(ProducerConfig.MAX_BLOCK_MS_CONFIG, "20"); // the time the .send() will block until throwing an exception
            //properties.setProperty(ProducerConfig.BUFFER_MEMORY_CONFIG, "20"); // The size of the send buffer which will fill up over time and fill back when the throughput to the broker increases.
            KafkaProducer<String, String> producer = new KafkaProducer<String, String>(properties);
            return producer;
        }

        @Override
        public String call() {
            List<Integer> sensorIds = mongoDataDao.getSensorMeta();
            fetchTime = mongoDataDao.getLastTime(sensorIds);
            while (true) {
                try {
                    Optional<List<SensorDataBean>> optional = Optional.of(sensorDataDao.getAllSensors(fetchTime, sensorIds));
                    List<SensorDataBean> sensorDataBeanList;
                    if (optional.isPresent()) {
                        sensorDataBeanList = optional.get();
                    } else {
                        logger.info("No connection found!!!");
                        try {
                            sleep(3000);
                        } catch (InterruptedException e) {
                            logger.info(e.getMessage());
                        }
                        continue;
                    }
                    if (sensorDataBeanList.size() > 0) {
                        logger.info(MessageFormat.format("Receiving {0} records", sensorDataBeanList.size()));
                        fetchTime = getMaxDate(sensorDataBeanList);
                        sensorDataBeanList.parallelStream().forEach(sensorDataBean ->
                                producer.send(new ProducerRecord<String, String>(topic, sensorDataBean.getSensorId() + "", gson.toJson(sensorDataBean)), new Callback() {
                                    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                                        logger.info("sensorId: " + sensorDataBean.getSensorId() + " recordDate: " + sensorDataBean.getRecordDate() + " rowId: " + sensorDataBean.getRowId());
                                        if (e == null) {
                                            logger.info("Received new metadata. \n" +
                                                    "Topic: " + recordMetadata.topic() + "\n" +
                                                    "Partition: " + recordMetadata.partition() + "\n" +
                                                    "Offset: " + recordMetadata.offset() + "\n" +
                                                    "Timestamp: " + recordMetadata.timestamp());
                                        } else {
                                            logger.info("Error while producing ", e);
                                        }
                                    }
                                })
                        );
                        producer.flush();
                    } else
                        logger.info("Nothing to process");
                    try {
                        sleep(10000);
                    } catch (InterruptedException e) {
                        logger.info(e.getMessage());
                    }
                    sensorIds = mongoDataDao.getSensorMeta(); // just for current business
                } catch (Exception e) {
                    logger.info("Error while connecting to mongoDB ", e);
                } finally {
                    latch.countDown();
                }
            }
        }

        private String getMaxDate(List<SensorDataBean> sensorDataBeanList) {
            Date date = new Date();
            String maxDate;
            Comparator<SensorDataBean> comparator = (p1, p2) -> date.getDate(p1.getRecordDate()).compareTo(date.getDate(p2.getRecordDate()));
            maxDate = sensorDataBeanList.stream().max(comparator).get().getRecordDate();
            return maxDate;
        }

    }
}
