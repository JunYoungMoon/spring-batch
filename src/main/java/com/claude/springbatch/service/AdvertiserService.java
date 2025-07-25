package com.claude.springbatch.service;

import com.claude.springbatch.dto.AdvertiserDataRequest;
import com.claude.springbatch.dto.CustomerDataResponse;
import com.claude.springbatch.entity.AdvertiserRequest;
import com.claude.springbatch.entity.Customer;
import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.repository.AdvertiserRequestRepository;
import com.claude.springbatch.repository.CustomerRepository;
import com.claude.springbatch.repository.CustomerDataRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdvertiserService {

    private final CustomerRepository customerRepository;
    private final CustomerDataRepository customerDataRepository;
    private final AdvertiserRequestRepository advertiserRequestRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdvertiserService(CustomerRepository customerRepository,
                           CustomerDataRepository customerDataRepository,
                           AdvertiserRequestRepository advertiserRequestRepository) {
        this.customerRepository = customerRepository;
        this.customerDataRepository = customerDataRepository;
        this.advertiserRequestRepository = advertiserRequestRepository;
    }

    public List<CustomerDataResponse> getCustomerDataForAdvertiser(AdvertiserDataRequest request) {
        List<CustomerData> customerDataList = filterCustomerData(request);
        
        return customerDataList.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public AdvertiserRequest createDataRequest(AdvertiserDataRequest request) {
        AdvertiserRequest advertiserRequest = new AdvertiserRequest();
        advertiserRequest.setAdvertiserId(request.getAdvertiserId());
        advertiserRequest.setRequestType("DATA_REQUEST");
        
        try {
            String criteriaJson = objectMapper.writeValueAsString(request);
            advertiserRequest.setCriteria(criteriaJson);
        } catch (Exception e) {
            advertiserRequest.setCriteria("Invalid criteria format");
        }
        
        advertiserRequest.setStatus(AdvertiserRequest.RequestStatus.PENDING);
        advertiserRequest.setRequestTime(LocalDateTime.now());
        advertiserRequest.setAiEnhanced(request.getAiEnhanced() != null ? request.getAiEnhanced() : false);
        
        return advertiserRequestRepository.save(advertiserRequest);
    }

    public AdvertiserRequest getRequestById(Long requestId) {
        return advertiserRequestRepository.findById(requestId).orElse(null);
    }

    public List<AdvertiserRequest> getRequestsByAdvertiserId(String advertiserId) {
        return advertiserRequestRepository.findByAdvertiserId(advertiserId);
    }

    public List<String> getAvailableSegments() {
        return customerRepository.findAll()
                .stream()
                .map(Customer::getSegment)
                .filter(segment -> segment != null && !segment.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getAvailableEventTypes() {
        return customerDataRepository.findAll()
                .stream()
                .map(CustomerData::getEventType)
                .filter(eventType -> eventType != null && !eventType.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<CustomerData> filterCustomerData(AdvertiserDataRequest request) {
        List<CustomerData> allData = customerDataRepository.findAll();
        
        return allData.stream()
                .filter(data -> filterByDateRange(data, request.getStartDate(), request.getEndDate()))
                .filter(data -> filterBySegments(data, request.getSegments()))
                .filter(data -> filterByEventTypes(data, request.getEventTypes()))
                .filter(data -> filterByChannels(data, request.getChannels()))
                .filter(data -> filterByLocation(data, request.getLocation()))
                .filter(data -> filterByAiEnhanced(data, request.getAiEnhanced()))
                .limit(request.getLimit() != null ? request.getLimit() : 1000)
                .collect(Collectors.toList());
    }

    private boolean filterByDateRange(CustomerData data, LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate == null && endDate == null) return true;
        LocalDateTime eventTime = data.getEventTimestamp();
        if (eventTime == null) return false;
        
        if (startDate != null && eventTime.isBefore(startDate)) return false;
        if (endDate != null && eventTime.isAfter(endDate)) return false;
        
        return true;
    }

    private boolean filterBySegments(CustomerData data, List<String> segments) {
        if (segments == null || segments.isEmpty()) return true;
        Customer customer = data.getCustomer();
        return customer != null && segments.contains(customer.getSegment());
    }

    private boolean filterByEventTypes(CustomerData data, List<String> eventTypes) {
        if (eventTypes == null || eventTypes.isEmpty()) return true;
        return eventTypes.contains(data.getEventType());
    }

    private boolean filterByChannels(CustomerData data, List<String> channels) {
        if (channels == null || channels.isEmpty()) return true;
        return channels.contains(data.getChannel());
    }

    private boolean filterByLocation(CustomerData data, String location) {
        if (location == null || location.isEmpty()) return true;
        return data.getLocation() != null && data.getLocation().contains(location);
    }

    private boolean filterByAiEnhanced(CustomerData data, Boolean aiEnhanced) {
        if (aiEnhanced == null) return true;
        return aiEnhanced.equals(data.getProcessedForAi());
    }

    private CustomerDataResponse convertToResponse(CustomerData data) {
        CustomerDataResponse response = new CustomerDataResponse();
        response.setId(data.getId());
        
        Customer customer = data.getCustomer();
        if (customer != null) {
            response.setCustomerId(customer.getCustomerId());
            response.setCustomerEmail(customer.getEmail());
            response.setCustomerSegment(customer.getSegment());
        }
        
        response.setEventType(data.getEventType());
        response.setEventCategory(data.getEventCategory());
        response.setEventTimestamp(data.getEventTimestamp());
        response.setDeviceType(data.getDeviceType());
        response.setBrowserType(data.getBrowserType());
        response.setLocation(data.getLocation());
        response.setChannel(data.getChannel());
        response.setCampaignId(data.getCampaignId());
        response.setRevenue(data.getRevenue());
        response.setCurrency(data.getCurrency());
        response.setAiFeatures(data.getAiFeatures());
        response.setCreatedAt(data.getCreatedAt());
        
        return response;
    }
}