package ktpisl.dao;

import ktpisl.models.SensorDataBean;

import java.sql.*;
import java.util.*;
import java.util.function.Supplier;

public class SensorDataDaoImpl extends BaseDao implements SensorDataDao {

    @Override
    public List<SensorDataBean> getAllSensors(String maxTime, List<Integer> sensorIds) {
        List<SensorDataBean> sensorDataBeanList = new ArrayList<>();
        try {
            Connection conn = getConnection();
            // As long as we have no enough space, we should consider a limited number of sensors;
            // Otherwise, list of sensors will be removed.
            StringBuilder stringBuilder = new StringBuilder("SELECT gatewayID, recordDate, sensorID, " +
                    "loop1, loop2, temp1, temp2, temp3, temp4, temp5, pulse1, pulse2," +
                    "analog1, analog2, Battery, RSSI, LQI,RowID " +
                    "FROM sensordata.sensorData where sensorID in(");
            sensorIds.forEach(sens -> stringBuilder.append(sens).append(", "));
            stringBuilder.append("0) and recordDate > ? order by sensorID, recordDate");
            PreparedStatement statement = conn.prepareStatement(stringBuilder.toString());
            statement.setString(1, maxTime);
            ResultSet rs = statement.executeQuery();
            SensorDataBean sensorDataBean;
            while (rs.next()) {
                sensorDataBean = parseLine(rs);
                sensorDataBeanList.add(sensorDataBean);
            }
            statement.close();
            removeConnection(conn);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sensorDataBeanList;
    }

    protected SensorDataBean parseLine(ResultSet rs) {
        // This method, transform readings into a meaningful sensor data schema
        Supplier<SensorDataBean> beanSupplier = SensorDataBean::new;
        SensorDataBean sensorDataBean = beanSupplier.get();
        try {
            sensorDataBean.setGatewayId(rs.getString(1));
            sensorDataBean.setRecordDate(rs.getString(2));
            sensorDataBean.setSensorId(rs.getInt(3));
            sensorDataBean.setLoop1(rs.getInt(4));
            sensorDataBean.setLoop2(rs.getInt(5));
            sensorDataBean.setTemp1(rs.getFloat(6));
            sensorDataBean.setTemp2(rs.getFloat(7));
            sensorDataBean.setTemp3(rs.getFloat(8));
            sensorDataBean.setTemp4(rs.getFloat(9));
            sensorDataBean.setTemp5(rs.getFloat(10));
            sensorDataBean.setPulse1(rs.getFloat(11));
            sensorDataBean.setPulse2(rs.getFloat(12));
            sensorDataBean.setAnalog1(rs.getFloat(13));
            sensorDataBean.setAnalog2(rs.getFloat(14));
            sensorDataBean.setBattery(rs.getFloat(15));
            sensorDataBean.setRSSI(rs.getFloat(16));
            sensorDataBean.setLQI(rs.getFloat(17));
            sensorDataBean.setRowId(rs.getInt(18));
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sensorDataBean;
    }
}
