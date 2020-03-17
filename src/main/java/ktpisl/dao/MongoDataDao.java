package ktpisl.dao;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoSocketWriteException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BsonField;
import ktpisl.models.SensorBean;
import ktpisl.models.SensorCodec;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.conversions.Bson;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.excludeId;
import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

public class MongoDataDao extends MongoDao {

    // This class is just for the current business in which a limited resources are in hand.
    // In real project, getSensorMeta is not required at all

    public String getLastTime(List<Integer> sensorIds) throws MongoTimeoutException, MongoSocketWriteException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd H:mm:ss");
        Bson sensMatch = in("sensor_id", sensorIds);
        Bson matchStage = Aggregates.match(sensMatch);
        Long groupId = 0L;
        BsonField max1 = Accumulators.max("maxTime", "$maxTime");
        Bson groupStage = Aggregates.group(groupId, max1);
        Bson project = excludeId();
        Bson projectStage = Aggregates.project(project);
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(matchStage);
        pipeline.add(groupStage);
        pipeline.add(projectStage);
        Document maxTime = null;
        try {
            maxTime = new Document("maxTime", format.parse("2019-12-01 00:00:00"));
        } catch (ParseException e) {
            e.printStackTrace();
        }
        Optional<Object> optional = Optional.ofNullable(sensorData.aggregate(pipeline).first());
        if (optional.isPresent())
            maxTime = (Document) optional.get();
        return format.format(maxTime.get("maxTime"));
    }

    public List<Integer> getSensorMeta() throws MongoTimeoutException, MongoSocketWriteException {
        SensorCodec sensorCodec = new SensorCodec();
        List<Integer> sensorList = new ArrayList<>();
        CodecRegistry codecRegistry =
                fromRegistries(MongoClientSettings.getDefaultCodecRegistry(), fromCodecs(sensorCodec));
        Bson queryFilter =
                and(
                        eq("activated", 1)
                );
        MongoCollection<SensorBean> sensors = db.getCollection("devices", SensorBean.class).withCodecRegistry(codecRegistry);
        List<SensorBean> sensorBeans = new ArrayList<>();
        sensors.find(queryFilter).into(sensorBeans);
        sensorBeans.forEach(res -> sensorList.add(res.getSensorId()));
        return sensorList;
    }
}
