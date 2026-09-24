package com.abhinav.agentic_ai_chatbot.repository;

import com.abhinav.agentic_ai_chatbot.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByNameIgnoreCase(String name);

    List<Employee> findByDepartmentIgnoreCase(String department);

    List<Employee> findByLocationIgnoreCase(String location);

    List<Employee> findByRoleContainingIgnoreCase(String role);
}