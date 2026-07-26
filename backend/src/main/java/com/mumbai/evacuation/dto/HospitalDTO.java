package com.mumbai.evacuation.dto;

import java.util.List;

public class HospitalDTO {
    private long id;
    private String name;
    private String region;
    private String area;
    private String address;
    private String emergencyPhone;
    private String alternatePhone;
    private double latitude;
    private double longitude;
    private List<String> specialties;
    private List<String> relevantDisasters;
    private int totalBeds;
    private boolean icuAvailable;

    public HospitalDTO() {}

    public HospitalDTO(long id, String name, String region, String area, String address, 
                       String emergencyPhone, String alternatePhone, double latitude, double longitude, 
                       List<String> specialties, List<String> relevantDisasters, int totalBeds, boolean icuAvailable) {
        this.id = id;
        this.name = name;
        this.region = region;
        this.area = area;
        this.address = address;
        this.emergencyPhone = emergencyPhone;
        this.alternatePhone = alternatePhone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.specialties = specialties;
        this.relevantDisasters = relevantDisasters;
        this.totalBeds = totalBeds;
        this.icuAvailable = icuAvailable;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getArea() { return area; }
    public void setArea(String area) { this.area = area; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getEmergencyPhone() { return emergencyPhone; }
    public void setEmergencyPhone(String emergencyPhone) { this.emergencyPhone = emergencyPhone; }

    public String getAlternatePhone() { return alternatePhone; }
    public void setAlternatePhone(String alternatePhone) { this.alternatePhone = alternatePhone; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public List<String> getSpecialties() { return specialties; }
    public void setSpecialties(List<String> specialties) { this.specialties = specialties; }

    public List<String> getRelevantDisasters() { return relevantDisasters; }
    public void setRelevantDisasters(List<String> relevantDisasters) { this.relevantDisasters = relevantDisasters; }

    public int getTotalBeds() { return totalBeds; }
    public void setTotalBeds(int totalBeds) { this.totalBeds = totalBeds; }

    public boolean isIcuAvailable() { return icuAvailable; }
    public void setIcuAvailable(boolean icuAvailable) { this.icuAvailable = icuAvailable; }
}
