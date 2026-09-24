package com.empresa.offboarding.repository;

import com.empresa.offboarding.entity.NotificationRecipient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRecipientRepository
        extends JpaRepository<NotificationRecipient, Long> {

    List<NotificationRecipient> findByActiveTrueOrderByEmailAsc();

    boolean existsByEmailIgnoreCase(String email);
}
