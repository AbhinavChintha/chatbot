package com.abhinav.agentic_ai_chatbot.service;

import com.abhinav.agentic_ai_chatbot.entity.Employee;
import com.abhinav.agentic_ai_chatbot.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

@Service
public class DatabaseService {

    private final EmployeeRepository employeeRepository;

    public DatabaseService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public String getEmployeeDetails(Long employeeId) {

        return employeeRepository.findById(employeeId)
                .map(employee -> String.format(
                        """
                        Employee Details:
                        ID: %d
                        Name: %s
                        Department: %s
                        Role: %s
                        Email: %s
                        Location: %s
                        """,
                        employee.getId(),
                        employee.getName(),
                        employee.getDepartment(),
                        employee.getRole(),
                        employee.getEmail(),
                        employee.getLocation()
                ))
                .orElse(
                        "Employee with ID " + employeeId + " was not found."
                );
    }
}