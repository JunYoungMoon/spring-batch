package com.claude.springbatch.repository;

import com.claude.springbatch.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    Optional<Customer> findByCustomerId(String customerId);
    
    List<Customer> findBySegment(String segment);
    
    List<Customer> findByStatus(String status);
    
    @Query("SELECT c FROM Customer c WHERE c.registrationDate BETWEEN :startDate AND :endDate")
    List<Customer> findByRegistrationDateBetween(@Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT c FROM Customer c JOIN c.customerData cd " +
           "WHERE cd.eventType = :eventType AND cd.eventTimestamp >= :since")
    List<Customer> findCustomersWithEventTypesSince(@Param("eventType") String eventType, 
                                                   @Param("since") LocalDateTime since);
}