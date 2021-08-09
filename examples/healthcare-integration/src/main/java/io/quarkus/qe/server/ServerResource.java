package io.quarkus.qe.server;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.agroal.api.AgroalDataSource;

@ApplicationScoped
@Path("/patient")
public class ServerResource {

    public static final int PORT = 6840;
    private static Logger log = LoggerFactory.getLogger(ServerResource.class);

    @Inject
    AgroalDataSource dataSource;

    @Path("/names")
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public List<String> getPatientNames() {
        List<String> results = new ArrayList<>();

        try (final Connection connection = dataSource.getConnection();
                final Statement statement = connection.createStatement();
                final ResultSet resultSet = statement.executeQuery("SELECT * FROM patient")) {

            while (resultSet.next()) {
                log.info("Got results: {}", resultSet.getString("name"));
                results.add(resultSet.getString("name"));
            }
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return results;
    }
}
