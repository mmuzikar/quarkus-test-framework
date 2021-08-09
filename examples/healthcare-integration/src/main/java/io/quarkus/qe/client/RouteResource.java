package io.quarkus.qe.client;

import java.io.IOException;
import java.util.Date;

import javax.enterprise.context.ApplicationScoped;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.hl7.HL7DataFormat;
import org.apache.camel.component.mllp.MllpAcknowledgementException;
import org.apache.camel.model.rest.RestBindingMode;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v26.datatype.PL;
import ca.uhn.hl7v2.model.v26.datatype.XAD;
import ca.uhn.hl7v2.model.v26.datatype.XCN;
import ca.uhn.hl7v2.model.v26.datatype.XPN;
import ca.uhn.hl7v2.model.v26.message.ADT_A01;
import ca.uhn.hl7v2.model.v26.segment.EVN;
import ca.uhn.hl7v2.model.v26.segment.MSH;
import ca.uhn.hl7v2.model.v26.segment.PID;
import ca.uhn.hl7v2.model.v26.segment.PV1;
import io.quarkus.qe.server.ServerResource;
import io.quarkus.qe.shared.PatientPojo;

@ApplicationScoped
public class RouteResource extends RouteBuilder {

    private static HL7DataFormat hl7DataFormat;
    int sequenceNumber = 0;

    @Override
    public void configure() throws Exception {
        onException(MllpAcknowledgementException.class).logStackTrace(true).log("CLIENT FAILED");
        restConfiguration().bindingMode(RestBindingMode.json);

        hl7DataFormat = new HL7DataFormat();
        bindToRegistry("hl7dataformat", hl7DataFormat);
        from("rest:post:createPatient?outType=io.quarkus.qe.shared.PatientPojo")
                .unmarshal().json(PatientPojo.class)
                .setHeader("patientName", simple("${body.getName()}"))
                .setBody(exchange -> {
                    System.out.println(exchange.getMessage().getBody());
                    PatientPojo patient = exchange.getMessage().getBody(PatientPojo.class);
                    System.out.println(patient);
                    final ADT_A01 message = new ADT_A01();
                    Date currentDate = new Date();
                    try {
                        message.initQuickstart("ADT", "A01", "P");

                        MSH mshSegment = message.getMSH();
                        mshSegment.getFieldSeparator().setValue("|");
                        mshSegment.getEncodingCharacters().setValue("^~\\&");
                        mshSegment.getSendingApplication().getNamespaceID().setValue("Our System");
                        mshSegment.getSendingFacility().getNamespaceID().setValue("Our Facility");
                        mshSegment.getReceivingApplication().getNamespaceID().setValue("Their Remote System");
                        mshSegment.getReceivingFacility().getNamespaceID().setValue(patient.getFacility());
                        mshSegment.getDateTimeOfMessage().setValue(currentDate);
                        mshSegment.getMessageControlID().setValue("#" + sequenceNumber);
                        mshSegment.getVersionID().getVersionID().setValue("2.6");

                        EVN evn = message.getEVN();
                        evn.getEventTypeCode().setValue("A01");
                        evn.getRecordedDateTime().setValue(currentDate);

                        PID pid = message.getPID();
                        XPN patientName = pid.getPatientName(0);
                        patientName.getFamilyName().getSurname().setValue(patient.getName());
                        final String id = Integer.toString(patient.getId());
                        pid.getPatientID().getIDNumber().setValue(id);
                        pid.getPatientIdentifierList(0).getIDNumber().setValue(id);
                        XAD patientAddress = pid.getPatientAddress(0);
                        patientAddress.getComment().setValue(patient.getAddress());

                        PV1 pv1 = message.getPV1();
                        pv1.getPatientClass().setValue(patient.getClassStatus()); // to represent an 'Outpatient'
                        PL assignedPatientLocation = pv1.getAssignedPatientLocation();
                        assignedPatientLocation.getFacility().getNamespaceID().setValue("Some Treatment Facility Name");
                        assignedPatientLocation.getPointOfCare().setValue("Some Point of Care");
                        pv1.getAdmissionType().setValue("ALERT");
                        XCN referringDoctor = pv1.getReferringDoctor(0);
                        referringDoctor.getIDNumber().setValue("99999999");
                        referringDoctor.getFamilyName().getSurname().setValue("Smith");
                        referringDoctor.getGivenName().setValue("Jack");
                        referringDoctor.getIdentifierTypeCode().setValue("456789");
                        pv1.getAdmitDateTime().setValue(currentDate);
                    } catch (HL7Exception | IOException e) {
                        e.printStackTrace();
                    }
                    return message;
                })
                .marshal(hl7DataFormat)
                .toF("mllp://localhost:%d", ServerResource.PORT)
                .log("Received back ${headers}")
                .transform(simple("Patient ${header.patientName} was created"));

        from("direct:movePatient")
                .log("Moving patient")
                .setBody(exchange -> {
                    final ADT_A01 message = new ADT_A01();
                    Date currentDate = new Date();
                    try {
                        message.initQuickstart("ADT", "A01", "P");

                        MSH mshSegment = message.getMSH();
                        mshSegment.getFieldSeparator().setValue("|");
                        mshSegment.getEncodingCharacters().setValue("^~\\&");
                        mshSegment.getSendingApplication().getNamespaceID().setValue("Our System");
                        mshSegment.getSendingFacility().getNamespaceID().setValue("Our Facility");
                        mshSegment.getReceivingApplication().getNamespaceID().setValue("Their Remote System");
                        mshSegment.getReceivingFacility().getNamespaceID()
                                .setValue(exchange.getMessage().getBody(String.class));
                        mshSegment.getDateTimeOfMessage().setValue(currentDate);
                        mshSegment.getMessageControlID().setValue("#" + sequenceNumber);
                        mshSegment.getVersionID().getVersionID().setValue("2.6");

                        EVN evn = message.getEVN();
                        evn.getEventTypeCode().setValue("A01");
                        evn.getRecordedDateTime().setValue(currentDate);

                        PID pid = message.getPID();
                        XPN patientName = pid.getPatientName(0);
                        patientName.getFamilyName().getSurname().setValue("Mouse");
                        patientName.getGivenName().setValue("Mickey");
                        pid.getPatientID().getIDNumber().setValue(exchange.getMessage().getHeader("patientId", String.class));
                        XAD patientAddress = pid.getPatientAddress(0);
                        patientAddress.getStreetAddress().getStreetOrMailingAddress().setValue("123 Main Street");
                        patientAddress.getCity().setValue("Lake Buena Vista");
                        patientAddress.getStateOrProvince().setValue("FL");
                        patientAddress.getCountry().setValue("USA");

                        PV1 pv1 = message.getPV1();
                        pv1.getPatientClass().setValue("O"); // to represent an 'Outpatient'
                        PL assignedPatientLocation = pv1.getAssignedPatientLocation();
                        assignedPatientLocation.getFacility().getNamespaceID()
                                .setValue(exchange.getMessage().getBody(String.class));
                        assignedPatientLocation.getPointOfCare().setValue("Some Point of Care");
                        pv1.getAdmissionType().setValue("ALERT");
                        XCN referringDoctor = pv1.getReferringDoctor(0);
                        referringDoctor.getIDNumber().setValue("99999999");
                        referringDoctor.getFamilyName().getSurname().setValue("Smith");
                        referringDoctor.getGivenName().setValue("Jack");
                        referringDoctor.getIdentifierTypeCode().setValue("456789");
                        pv1.getAdmitDateTime().setValue(currentDate);
                    } catch (HL7Exception | IOException e) {
                        e.printStackTrace();
                    }
                    return message;
                })
                .marshal(hl7DataFormat)
                .log("Sending patient to a different facility")
                .toF("mllp://localhost:%d", ServerResource.PORT);
    }
}
