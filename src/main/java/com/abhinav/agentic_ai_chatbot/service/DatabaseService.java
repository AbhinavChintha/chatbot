package com.abhinav.agentic_ai_chatbot.service;

import com.abhinav.agentic_ai_chatbot.entity.Employee;
import com.abhinav.agentic_ai_chatbot.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatabaseService {

    private final EmployeeRepository employeeRepository;

    public DatabaseService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Employee getEmployeeDetails(Long employeeId) {

        return employeeRepository
                .findById(employeeId)
                .orElse(null);
    }

    public Employee getEmployeeDetailsByName(String name) {

        return employeeRepository
                .findByNameIgnoreCase(name)
                .orElse(null);
    }

    public List<Employee> getEmployeesByDepartment(
            String department) {

        return employeeRepository
                .findByDepartmentIgnoreCase(department);
    }

    public List<Employee> getEmployeesByLocation(
            String location) {

        return employeeRepository
                .findByLocationIgnoreCase(location);
    }

    public List<Employee> getEmployeesByRole(
            String role) {

        return employeeRepository
                .findByRoleContainingIgnoreCase(role);
    }
}