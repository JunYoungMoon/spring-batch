package com.claude.springbatch.config;

import com.claude.springbatch.entity.Advertiser;
import com.claude.springbatch.entity.Customer;
import com.claude.springbatch.entity.CustomerData;
import com.claude.springbatch.repository.AdvertiserRepository;
import com.claude.springbatch.repository.CustomerRepository;
import com.claude.springbatch.repository.CustomerDataRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(CustomerRepository customerRepository, 
                                 CustomerDataRepository customerDataRepository,
                                 AdvertiserRepository advertiserRepository) {
        return args -> {
            if (customerRepository.count() == 0) {
                initializeSampleData(customerRepository, customerDataRepository, advertiserRepository);
            }
        };
    }

    private void initializeSampleData(CustomerRepository customerRepository, 
                                    CustomerDataRepository customerDataRepository,
                                    AdvertiserRepository advertiserRepository) {
        
        // Initialize advertisers first
        List<Advertiser> advertisers = createSampleAdvertisers();
        advertiserRepository.saveAll(advertisers);
        
        List<Customer> customers = createSampleCustomers();
        customerRepository.saveAll(customers);

        List<CustomerData> customerDataList = createSampleCustomerData(customers);
        customerDataRepository.saveAll(customerDataList);
    }
    
    private List<Advertiser> createSampleAdvertisers() {
        return Arrays.asList(
            createAdvertiser("ADV001", "TechCorp", "contact@techcorp.com", "John Smith", 0),
            createAdvertiser("ADV002", "FashionBrand", "marketing@fashionbrand.com", "Sarah Johnson", 1),
            createAdvertiser("ADV003", "FoodDelivery", "ads@fooddelivery.com", "Mike Chen", 2),
            createAdvertiser("ADV004", "TravelAgency", "digital@travel.com", "Emily Davis", 3),
            createAdvertiser("ADV005", "FinanceApp", "growth@financeapp.com", "David Wilson", 4)
        );
    }
    
    private Advertiser createAdvertiser(String advertiserId, String name, String email, 
                                      String contactPerson, int priority) {
        Advertiser advertiser = new Advertiser();
        advertiser.setAdvertiserId(advertiserId);
        advertiser.setName(name);
        advertiser.setEmail(email);
        advertiser.setContactPerson(contactPerson);
        advertiser.setStatus(Advertiser.AdvertiserStatus.ACTIVE);
        advertiser.setBatchEnabled(true);
        advertiser.setRotationPriority(priority);
        advertiser.setMaxFailures(3);
        advertiser.setFailureCount(0);
        advertiser.setLastBatchStatus(Advertiser.BatchStatus.PENDING);
        return advertiser;
    }

    private List<Customer> createSampleCustomers() {
        LocalDateTime now = LocalDateTime.now();
        
        return Arrays.asList(
            createCustomer("CUST001", "john.doe@email.com", "John", "Doe", 
                         "+1-555-0101", now.minusDays(30), "premium", "active"),
            createCustomer("CUST002", "jane.smith@email.com", "Jane", "Smith", 
                         "+1-555-0102", now.minusDays(45), "standard", "active"),
            createCustomer("CUST003", "bob.johnson@email.com", "Bob", "Johnson", 
                         "+1-555-0103", now.minusDays(10), "premium", "active"),
            createCustomer("CUST004", "alice.wilson@email.com", "Alice", "Wilson", 
                         "+1-555-0104", now.minusDays(60), "standard", "inactive"),
            createCustomer("CUST005", "charlie.brown@email.com", "Charlie", "Brown", 
                         "+1-555-0105", now.minusDays(5), "vip", "active")
        );
    }

    private Customer createCustomer(String customerId, String email, String firstName, 
                                  String lastName, String phone, LocalDateTime regDate, 
                                  String segment, String status) {
        Customer customer = new Customer();
        customer.setCustomerId(customerId);
        customer.setEmail(email);
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setPhoneNumber(phone);
        customer.setRegistrationDate(regDate);
        customer.setSegment(segment);
        customer.setStatus(status);
        return customer;
    }

    private List<CustomerData> createSampleCustomerData(List<Customer> customers) {
        Random random = new Random();
        LocalDateTime now = LocalDateTime.now();
        
        String[] eventTypes = {"purchase", "view", "click", "search", "cart", "signup"};
        String[] eventCategories = {"product", "category", "promotion", "account"};
        String[] deviceTypes = {"desktop", "mobile", "tablet"};
        String[] browserTypes = {"chrome", "firefox", "safari", "edge"};
        String[] locations = {"New York", "Los Angeles", "Chicago", "Houston", "Phoenix"};
        String[] channels = {"direct", "email", "social", "paid_search", "organic"};
        String[] campaigns = {"CAMP001", "CAMP002", "CAMP003", null, null}; // 60% have campaigns

        return customers.stream()
                .flatMap(customer -> {
                    // Generate 3-8 events per customer
                    int eventCount = 3 + random.nextInt(6);
                    return java.util.stream.IntStream.range(0, eventCount)
                            .mapToObj(i -> {
                                CustomerData data = new CustomerData();
                                data.setCustomer(customer);
                                data.setEventType(eventTypes[random.nextInt(eventTypes.length)]);
                                data.setEventCategory(eventCategories[random.nextInt(eventCategories.length)]);
                                data.setEventTimestamp(now.minusHours(random.nextInt(168))); // Last week
                                data.setEventData(generateEventData(data.getEventType()));
                                data.setDeviceType(deviceTypes[random.nextInt(deviceTypes.length)]);
                                data.setBrowserType(browserTypes[random.nextInt(browserTypes.length)]);
                                data.setLocation(locations[random.nextInt(locations.length)]);
                                data.setChannel(channels[random.nextInt(channels.length)]);
                                data.setCampaignId(campaigns[random.nextInt(campaigns.length)]);
                                
                                // Add revenue for purchase events
                                if ("purchase".equals(data.getEventType())) {
                                    data.setRevenue(BigDecimal.valueOf(10 + random.nextDouble() * 500));
                                    data.setCurrency("USD");
                                }
                                
                                // 30% of data is already processed for AI
                                data.setProcessedForAi(random.nextBoolean() && random.nextBoolean());
                                if (data.getProcessedForAi()) {
                                    data.setAiFeatures(generateSampleAiFeatures());
                                }
                                
                                return data;
                            });
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private String generateEventData(String eventType) {
        switch (eventType) {
            case "purchase":
                return "{\"productId\":\"PROD123\",\"quantity\":2,\"price\":49.99}";
            case "view":
                return "{\"pageUrl\":\"/products/electronics\",\"duration\":120}";
            case "search":
                return "{\"query\":\"wireless headphones\",\"results\":45}";
            case "cart":
                return "{\"action\":\"add\",\"productId\":\"PROD456\"}";
            default:
                return "{\"action\":\"" + eventType + "\",\"timestamp\":\"" + LocalDateTime.now() + "\"}";
        }
    }

    private String generateSampleAiFeatures() {
        return "{\"customerSegment\":\"premium\",\"recency\":85.5,\"frequency\":1.0," +
               "\"behaviorPattern\":\"TRANSACTIONAL\",\"locationScore\":1.0," +
               "\"deviceFingerprint\":\"DES_CHR\",\"campaignEngagement\":1}";
    }
}