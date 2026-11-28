package dev.sivalabs.helpdesk.staff;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Staff-only details; shares its primary key with the user. */
@Entity
@Table(name = "staff_profiles")
class StaffProfileEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String mobile;

    @Column(name = "permanent_address")
    private String permanentAddress;

    @Version
    private Long version;

    protected StaffProfileEntity() {}

    StaffProfileEntity(Long userId, String mobile, String permanentAddress) {
        this.userId = userId;
        this.mobile = mobile;
        this.permanentAddress = permanentAddress;
    }

    public Long getUserId() {
        return userId;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getPermanentAddress() {
        return permanentAddress;
    }

    public void setPermanentAddress(String permanentAddress) {
        this.permanentAddress = permanentAddress;
    }
}
