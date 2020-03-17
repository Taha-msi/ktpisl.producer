package ktpisl.dao;

import ktpisl.models.SensorDataBean;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RT2DaoImpl extends SensorDataDaoImpl {
    public RT2DaoImpl() {
    }

    @Override
    public List<SensorDataBean> getAllSensors(String fetchTime, List<Integer> sensorIds) {
        List<SensorDataBean> sensorDataBeanList = new ArrayList<>();
        try {
            Connection conn = getConnection();
            SensorDataBean sensorDataBean;
            StringBuilder stringBuilder = new StringBuilder("SELECT gatewayID, recordDate, sensorID, " +
                    "loop1, loop2, temp1, temp2, temp3, temp4, temp5, pulse1, pulse2," +
                    "analog1, analog2, 0 as Battery, 0 as  RSSI, 0 as LQI,RowID " +
                    "FROM rt2.sensorData where sensorID in(");
            sensorIds.forEach(sens -> stringBuilder.append(sens).append(", "));
            stringBuilder.append("0) and recordDate > ? order by sensorID, recordDate");
            PreparedStatement statement = conn.prepareStatement(stringBuilder.toString());
            statement.setString(1, fetchTime);
            ResultSet rs = statement.executeQuery();
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
}
