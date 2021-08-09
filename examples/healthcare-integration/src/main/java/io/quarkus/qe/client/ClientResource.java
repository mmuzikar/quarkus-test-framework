package io.quarkus.qe.client;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.FluentProducerTemplate;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agroal.api.AgroalDataSource;

@Path("/client")
@ApplicationScoped
public class ClientResource {

    @Inject
    AgroalDataSource dataSource;

    @Inject
    FluentProducerTemplate producerTemplate;

    @Inject
    ConsumerTemplate consumerTemplate;

    private static Logger log = LoggerFactory.getLogger(ClientResource.class);

    @Path("/facility/{id}")
    @POST
    public void movePatient(@PathParam("id") String id, String body) {
        producerTemplate.withBody(body).withHeader("patientId", id).to("direct:movePatient").send();
    }

    @Path("/patient/{id}")
    @GET
    public String getPatient(@PathParam("id") int id) {

        try (final Connection connection = dataSource.getConnection();
                final PreparedStatement statement = connection.prepareStatement("SELECT * FROM patient WHERE id = ?")) {
            statement.setInt(1, id);
            final ResultSet resultSet = statement.executeQuery();

            ResultSetMetaData rsmd = resultSet.getMetaData();
            JSONArray json = new JSONArray();
            while (resultSet.next()) {
                int numColumns = rsmd.getColumnCount();
                JSONObject obj = new JSONObject();
                for (int i = 1; i <= numColumns; i++) {
                    String column_name = rsmd.getColumnName(i);
                    obj.put(column_name, resultSet.getObject(column_name));
                }
                json.put(obj);
            }
            resultSet.close();
            return json.toString();
        } catch (SQLException throwables) {
            log.error("SQL exception ", throwables);
            throwables.printStackTrace();
        }
        log.error("no content found");
        return null;
    }
}
