package ktpisl.models;


import org.bson.codecs.pojo.annotations.BsonProperty;


public class SensorBean {

    @BsonProperty("sensor_id")
    private int sensorId;

    public int getSensorId() {
        return sensorId;
    }

    public void setSensorId(int sensorId) {
        this.sensorId = sensorId;
    }


}
