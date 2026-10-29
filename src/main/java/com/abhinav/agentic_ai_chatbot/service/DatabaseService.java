package com.abhinav.agentic_ai_chatbot.service;

import com.abhinav.agentic_ai_chatbot.entity.Employee;
import com.abhinav.agentic_ai_chatbot.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatabaseService {

    private final EmployeeRepository employeeRepository;
    private final ChatLatencyMetrics latencyMetrics;

    public DatabaseService(EmployeeRepository employeeRepository, ChatLatencyMetrics latencyMetrics) {
        this.employeeRepository = employeeRepository;
        this.latencyMetrics = latencyMetrics;
    }

    public Employee getEmployeeDetails(Long employeeId) {

        return latencyMetrics.measure("database_query", () -> employeeRepository
                .findById(employeeId)
                .orElse(null));
    }

    public Employee getEmployeeDetailsByName(String name) {

        return latencyMetrics.measure("database_query", () -> employeeRepository
                .findByNameIgnoreCase(name)
                .orElse(null));
    }

    public List<Employee> getEmployeesByDepartment(
            String department) {

        return latencyMetrics.measure("database_query", () -> employeeRepository
                .findByDepartmentIgnoreCase(department));
    }

    public List<Employee> getEmployeesByLocation(
            String location) {

        return latencyMetrics.measure("database_query", () -> employeeRepository
                .findByLocationIgnoreCase(location));
    }

    public List<Employee> getEmployeesByRole(
            String role) {

        return latencyMetrics.measure("database_query", () -> employeeRepository
                .findByRoleContainingIgnoreCase(role));
    }
}