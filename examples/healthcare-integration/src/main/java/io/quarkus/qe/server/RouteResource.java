package io.quarkus.qe.server;

import static org.apache.camel.component.hl7.HL7.hl7terser;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hl7.HL7DataFormat;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.hl7v2.model.v26.message.ADT_A01;
import io.agroal.api.AgroalDataSource;

@ApplicationScoped
public class RouteResource extends RouteBuilder {

    private static HL7DataFormat hl7DataFormat;

    private static final Logger log = LoggerFactory.getLogger(RouteResource.class);

    @Inject
    AgroalDataSource dataSource;

    Predicate patientExists() {
        return (exchange) -> {
            final ADT_A01 message = exchange.getMessage().getBody(ADT_A01.class);
            try (final Connection connection = dataSource.getConnection();
                    final Statement statement = connection.createStatement();
                    final ResultSet resultSet = statement.executeQuery(
                            "SELECT * FROM patient WHERE id = " + message.getPID().getPatientID().getIDNumber().getValue());) {
                return resultSet.next();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
            return false;
        };
    }

    @Override
    public void configure() throws Exception {
        String sql = IOUtils.resourceToString("/import.sql", StandardCharsets.UTF_8);
        sql.lines().forEach(line -> {
            try (final Connection connection = dataSource.getConnection();
                    final Statement statement = connection.createStatement();) {
                statement.execute(line);
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        });

        hl7DataFormat = new HL7DataFormat();
        fromF("mllp://localhost:%d", ServerResource.PORT)
                .log("Received MLLP Message ${headers}")
                .unmarshal(hl7DataFormat)
                .choice()
                .when(hl7terser("PID-2-1").isNull()).throwException(new InvalidKeyException("Patient must have an ID"))
                .when(patientExists())
                .log("PATIENT EXISTS, updating...")
                .process(exchange -> {
                    final ADT_A01 message = exchange.getMessage().getBody(ADT_A01.class);
                    exchange.getMessage().setBody(new Object[] {
                            message.getPID().getPatientName(0).getGivenName() + " "
                                    + message.getPID().getPatientName(0).getFamilyName(),
                            message.getPID().getPatientAddress(0).toString(),
                            message.getPV1().getPatientClass().getValue(),
                            message.getMSH().getReceivingFacility().getNamespaceID().getValue(),
                            Integer.parseInt(message.getPID().getPatientID().getIDNumber().getValue())
                    });
                })
                .to("sql:UPDATE patient SET name = #, address = #, class = #, facility = # where id = #")
                .log("${headers} ${body}")
                .otherwise()
                .process(exchange -> {
                    final ADT_A01 message = exchange.getMessage().getBody(ADT_A01.class);
                    exchange.getMessage().setBody(new Object[] {
                            Integer.parseInt(message.getPID().getPatientID().getIDNumber().getValue()),
                            message.getPID().getPatientName(0).getGivenName() + " "
                                    + message.getPID().getPatientName(0).getFamilyName(),
                            message.getPID().getPatientAddress(0).toString(),
                            message.getPV1().getPatientClass().getValue(),
                            message.getMSH().getReceivingFacility().getNamespaceID().getValue()
                    });
                })
                .to("sql:INSERT INTO mydb.patient VALUES (#, #, #, #, #)")
                .log("sent sql ${body}")
                .endChoice();
    }
}
