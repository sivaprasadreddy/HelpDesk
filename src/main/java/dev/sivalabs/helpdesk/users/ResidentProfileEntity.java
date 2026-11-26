package dev.sivalabs.helpdesk.users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Resident-only details; shares its primary key with the user. */
@Entity
@Table(name = "resident_profiles")
class ResidentProfileEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "flat_number", nullable = false)
    private String flatNumber;

    @Column(nullable = false)
    private String mobile;

    @Column(name = "permanent_address")
    private String permanentAddress;

    @Version
    private Long version;

    protected ResidentProfileEntity() {}

    ResidentProfileEntity(Long userId, String flatNumber, String mobile, String permanentAddress) {
        this.userId = userId;
        this.flatNumber = flatNumber;
        this.mobile = mobile;
        this.permanentAddress = permanentAddress;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFlatNumber() {
        return flatNumber;
    }

    public String getMobile() {
        return mobile;
    }

    public String getPermanentAddress() {
        return permanentAddress;
    }
}
