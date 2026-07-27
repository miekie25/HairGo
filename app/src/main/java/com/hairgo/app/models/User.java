package com.hairgo.app.models;

import com.google.firebase.Timestamp;

public class User {
    private String uid;
    private String name;
    private String surname;
    private String email;
    private String phoneNumber;
    private String role;        // "client", "owner", or "admin"
    private Timestamp createdAt;
    private boolean isDeleted;

    // Empty constructor required by Firebase
    public User() {}

    public User(String uid, String name, String surname, String email,
                String phoneNumber, String role, Timestamp createdAt, boolean isDeleted) {
        this.uid = uid;
        this.name = name;
        this.surname = surname;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.role = role;
        this.createdAt = createdAt;
        this.isDeleted = isDeleted;
    }

    // Getters and Setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSurname() { return surname; }
    public void setSurname(String surname) { this.surname = surname; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}