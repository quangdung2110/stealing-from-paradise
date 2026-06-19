package com.flashsale.identityservice.service;

import com.flashsale.identityservice.domain.model.Address;
import com.flashsale.identityservice.domain.model.Role;
import com.flashsale.identityservice.domain.model.User;
import com.flashsale.identityservice.domain.repository.AddressRepository;
import com.flashsale.identityservice.domain.repository.RoleRepository;
import com.flashsale.identityservice.domain.repository.UserRepository;
import com.flashsale.identityservice.dto.request.ChangePasswordRequest;
import com.flashsale.identityservice.dto.request.AddressCreateRequest;
import com.flashsale.identityservice.dto.request.AddressUpdateRequest;
import com.flashsale.identityservice.dto.request.NotificationPreferencesUpdateRequest;
import com.flashsale.identityservice.dto.request.UserProfileUpdateRequest;
import com.flashsale.identityservice.dto.response.NotificationPreferencesResponse;
import com.flashsale.commonlib.dto.PageResponse;
import com.flashsale.commonlib.event.KafkaTopics;
import com.flashsale.identityservice.dto.response.AddressResponse;
import com.flashsale.identityservice.dto.response.AdminUserResponse;
import com.flashsale.identityservice.dto.response.SellerPublicResponse;
import com.flashsale.identityservice.dto.response.UserProfileResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        User user = getUserById(userId);
        String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(userId)
                .map(Role::getRoleName)
                .orElse("BUYER");

        return UserProfileResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .avatarUrl(user.getAvatarUrl())
                .notificationPreferences(user.getNotificationPreferences())
                .build();
    }

    @Transactional(readOnly = true)
    public SellerPublicResponse getSellerPublicInfo(Long sellerId) {
        User user = getUserById(sellerId);
        String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(sellerId)
                .map(Role::getRoleName)
                .orElse("BUYER");

        return SellerPublicResponse.builder()
                .sellerId(user.getId())
                .sellerName(user.getFullName() != null ? user.getFullName() : user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .joinedAt(user.getCreatedAt())
                .productCount(0)
                .build();
    }

    @Transactional
    public UserProfileResponse updateUserProfile(Long userId, UserProfileUpdateRequest request) {
        User user = getUserById(userId);

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            userRepository.findByPhone(request.getPhone())
                    .filter(existing -> !existing.getId().equals(userId))
                    .ifPresent(ignored -> { throw new RuntimeException("Phone number already in use"); });
            user.setPhone(request.getPhone());
        }

        User saved = userRepository.save(user);
        UserProfileResponse response = getUserProfile(userId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", KafkaTopics.ACCOUNT_UPDATED);
        payload.put("user_id", saved.getId());
        payload.put("username", saved.getUsername());
        payload.put("email", saved.getEmail());
        payload.put("phone", saved.getPhone());
        payload.put("full_name", saved.getFullName());
        payload.put("updated_at", Instant.now().toString());
        publishEvent(KafkaTopics.ACCOUNT_UPDATED, String.valueOf(userId), payload);
        return response;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getUserAddresses(Long userId) {
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(this::toAddressResponse)
                .toList();
    }

    @Transactional
    public AddressResponse addAddress(Long userId, AddressCreateRequest request) {
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.clearDefaultForUser(userId);
        }

        Address address = Address.builder()
                .userId(userId)
                .provinceId(request.getProvinceId())
                .districtId(request.getDistrictId())
                .fullAddress(request.getFullAddress())
                .isDefault(request.getIsDefault() != null ? request.getIsDefault() : false)
                .build();

        if (!address.getIsDefault() && addressRepository.countByUserId(userId) == 0) {
            address.setIsDefault(true);
        }

        return toAddressResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressUpdateRequest request) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        if (request.getProvinceId() != null) {
            address.setProvinceId(request.getProvinceId());
        }
        if (request.getDistrictId() != null) {
            address.setDistrictId(request.getDistrictId());
        }
        if (request.getFullAddress() != null) {
            address.setFullAddress(request.getFullAddress());
        }
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            addressRepository.clearDefaultForUserExcept(userId, addressId);
            address.setIsDefault(true);
        }

        return toAddressResponse(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new RuntimeException("Address not found"));

        boolean wasDefault = address.getIsDefault();
        addressRepository.delete(address);

        if (wasDefault) {
            addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                    .stream()
                    .filter(a -> !a.getId().equals(addressId))
                    .findFirst()
                    .ifPresent(first -> {
                        first.setIsDefault(true);
                        addressRepository.save(first);
                    });
        }
    }

    @Transactional
    public void registerAsSeller(Long userId) {
        User user = getUserById(userId);

        boolean alreadySeller = roleRepository.findFirstByUserIdOrderByIdAsc(userId)
                .map(r -> "SELLER".equals(r.getRoleName()))
                .orElse(false);

        if (alreadySeller) {
            throw new RuntimeException("User is already a seller");
        }

        Role role = roleRepository.findFirstByUserIdOrderByIdAsc(userId)
                .orElse(Role.builder().userId(userId).build());
        role.setRoleName("SELLER");
        roleRepository.save(role);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_type", KafkaTopics.SELLER_REGISTERED);
        payload.put("user_id", user.getId());
        payload.put("username", user.getUsername());
        payload.put("email", user.getEmail());
        payload.put("full_name", user.getFullName());
        payload.put("registered_at", Instant.now().toString());
        publishEvent(KafkaTopics.SELLER_REGISTERED, String.valueOf(userId), payload);
        log.info("User {} registered as seller", userId);
    }

    private void publishEvent(String topic, String key, Map<String, Object> payload) {
        try {
            kafkaTemplate.send(topic, key, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("Failed to publish identity event topic={}, key={}: {}", topic, key, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String status, String search, Pageable pageable) {
        Specification<User> spec = null;

        if (status != null && !status.isBlank()) {
            spec = (root, query, cb) -> cb.equal(root.get("status"), status);
        }

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.toLowerCase() + "%";
            Specification<User> searchSpec = (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("username")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern),
                    cb.and(
                            cb.isNotNull(root.get("phone")),
                            cb.like(cb.lower(root.get("phone")), pattern)
                    )
            );
            spec = (spec == null) ? searchSpec : spec.and(searchSpec);
        }

        Page<User> userPage = (spec != null)
                ? userRepository.findAll(spec, pageable)
                : userRepository.findAll(pageable);

        List<AdminUserResponse> responses = userPage.getContent().stream()
                .map(user -> {
                    String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(user.getId())
                            .map(Role::getRoleName)
                            .orElse("BUYER");

                    return AdminUserResponse.builder()
                            .userId(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .phone(user.getPhone())
                            .fullName(user.getFullName())
                            .status(user.getStatus())
                            .role(roleName)
                            .createdAt(user.getCreatedAt())
                            .build();
                })
                .toList();

        return PageResponse.<AdminUserResponse>builder()
                .content(responses)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .last(userPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getAdminUserDetail(Long userId) {
        User user = getUserById(userId);
        String roleName = roleRepository.findFirstByUserIdOrderByIdAsc(userId)
                .map(Role::getRoleName)
                .orElse("BUYER");

        return AdminUserResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .role(roleName)
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getUserById(userId);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new RuntimeException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        log.info("Password changed for user {}", userId);
    }

    @Transactional
    public void updateAvatarUrl(Long userId, String avatarUrl) {
        User user = getUserById(userId);
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        log.info("Avatar updated for user {}", userId);
    }

    @Transactional
    public NotificationPreferencesResponse getNotificationPreferences(Long userId) {
        User user = getUserById(userId);
        Map<String, Boolean> prefs = user.getNotificationPreferences();
        return NotificationPreferencesResponse.builder()
                .preferences(prefs != null ? prefs : Map.of())
                .build();
    }

    @Transactional
    public NotificationPreferencesResponse updateNotificationPreferences(
            Long userId,
            NotificationPreferencesUpdateRequest request) {
        User user = getUserById(userId);
        Map<String, Boolean> merged = new java.util.HashMap<>();
        // Start with defaults, then apply saved + new
        merged.put("email_order", false);
        merged.put("email_promo", false);
        merged.put("email_flashsale", false);
        merged.put("push_order", false);
        merged.put("push_promo", false);
        if (user.getNotificationPreferences() != null) {
            merged.putAll(user.getNotificationPreferences());
        }
        if (request.getPreferences() != null) {
            merged.putAll(request.getPreferences());
        }
        user.setNotificationPreferences(merged);
        userRepository.save(user);
        log.info("Notification preferences updated for user {}", userId);
        return NotificationPreferencesResponse.builder()
                .preferences(merged)
                .build();
    }

    private AddressResponse toAddressResponse(Address address) {
        return AddressResponse.builder()
                .addressId(address.getId())
                .provinceId(address.getProvinceId())
                .districtId(address.getDistrictId())
                .fullAddress(address.getFullAddress())
                .isDefault(address.getIsDefault())
                .build();
    }
}
