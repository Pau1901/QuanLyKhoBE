package com.techbytedev.warehousemanagement.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UserCreateRequest {
    private String username;
    private String email;
    private String fullName;
    private String password;
    private String phoneNumber;
    private String address;
    
    @JsonProperty("isActive")
    private Boolean isActive;
    
    private Integer roleId; // Changed from roleName to roleId
}
