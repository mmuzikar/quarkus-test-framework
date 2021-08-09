package io.quarkus.qe.shared;

import ca.uhn.hl7v2.model.v26.message.ADT_A01;

public class PatientPojo {

    private int id;
    private String name;
    private String facility;
    private String classStatus;
    private String address;

    public PatientPojo() {
        //required for jackson
    }

    public PatientPojo(int id, String name, String facility, String classStatus, String address) {
        this.id = id;
        this.name = name;
        this.facility = facility;
        this.classStatus = classStatus;
        this.address = address;
    }

    public PatientPojo(ADT_A01 message) {
        this.name = message.getPID().getPatientName(0).getGivenName().toString();
        this.address = message.getPID().getPatientAddress(0).getComment().toString();
        this.classStatus = message.getPV1().getPatientClass().getValue();
        this.facility = message.getMSH().getReceivingFacility().getNamespaceID().getValue();
        this.id = Integer.parseInt(message.getPID().getPatientID().getIDNumber().getValue());
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getFacility() {
        return facility;
    }

    public String getClassStatus() {
        return classStatus;
    }

    public String getAddress() {
        return address;
    }
}
