package ktpisl.dao;

import ktpisl.models.SensorDataBean;

import java.util.List;

@FunctionalInterface
public interface SensorDataDao {
    public List<SensorDataBean> getAllSensors(String fetchTime, List<Integer> sensorIds);
}
