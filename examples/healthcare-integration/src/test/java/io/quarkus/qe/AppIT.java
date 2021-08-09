package io.quarkus.qe;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.qe.shared.PatientPojo;
import io.quarkus.test.bootstrap.DefaultService;
import io.quarkus.test.bootstrap.RestService;
import io.quarkus.test.scenarios.QuarkusScenario;
import io.quarkus.test.services.Container;
import io.quarkus.test.services.QuarkusApplication;

@QuarkusScenario
public class AppIT {

    private static final Logger LOG = LoggerFactory.getLogger(AppIT.class);

    static final String MYSQL_USER = "user";
    static final String MYSQL_PASSWORD = "user";
    static final String MYSQL_DATABASE = "mydb";
    static final int MYSQL_PORT = 3306;

    @Container(image = "mysql/mysql-server:8.0", port = MYSQL_PORT, expectedLog = "port: 3306  MySQL Community Server")
    static DefaultService database = new DefaultService()
            .withProperty("MYSQL_USER", MYSQL_USER)
            .withProperty("MYSQL_PASSWORD", MYSQL_PASSWORD)
            .withProperty("MYSQL_DATABASE", MYSQL_DATABASE);

    @QuarkusApplication
    static RestService app = new RestService()
            .withProperty("quarkus.datasource.username", MYSQL_USER)
            .withProperty("quarkus.datasource.password", MYSQL_PASSWORD)
            .withProperty("quarkus.datasource.jdbc.url",
                    () -> database.getHost().replace("http", "jdbc:mysql") + ":" + database.getPort() + "/" + MYSQL_DATABASE);

    @Test
    public void testTCPConnection() throws IOException {
        LOG.info("Testing started");
        Awaitility.await().timeout(Duration.of(2, ChronoUnit.MINUTES)).untilAsserted(() -> {
            app.given().body(new PatientPojo(4, "Mickey Mouse", "Our facility", "I", "Disney Cryo Chamber"))
                    .when()
                    .post("/createPatient")
                    .then()
                    .body(Matchers.containsString("Mickey"));
        });
    }

    @Test
    public void movePerson() throws Exception {
        app.given().when().body("Home").post("/client/facility/1").then().statusCode(204);
        Awaitility.await().timeout(Duration.of(2, ChronoUnit.MINUTES)).untilAsserted(() -> {
            app.given().when().get("/client/patient/1").then().body(Matchers.containsString("Home"));
        });
    }

    @Test
    public void invalidPerson() throws Exception {
        app.given().when()
                .body(new PatientPojo(-5, "Shatisha Aziz", "Some facility", "N", "Closer Road 5130, Castleberry, Cuba, 716300"))
                .post("/createPatient")
                .then()
                .log()
                .all()
                .statusCode(200);
    }
}
