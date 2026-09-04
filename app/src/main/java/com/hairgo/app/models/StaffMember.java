package com.hairgo.app.models;

public class StaffMember {

    private String id;
    private String name;
    private String role;
    private String phone;

    public StaffMember(String id, String name, String role, String phone) {
        this.id = id;
        this.name = name;
        this.role = role;
        this.phone = phone;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public String getPhone() { return phone; }
}
