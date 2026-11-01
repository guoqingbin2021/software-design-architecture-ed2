package edu.textbook.selection.domain;

import jakarta.persistence.*;

@Entity @Table(name="app_user")
public class UserAccount {
    @Id @Column(length=40) private String username;
    @Column(name="password_hash",nullable=false,length=100) private String passwordHash;
    @Column(nullable=false,length=20) private String role;
    @Column(name="person_id",nullable=false) private Long personId;
    protected UserAccount() {}
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public Long getPersonId() { return personId; }
}
