package com.abhinav.agentic_ai_chatbot.repository;

import com.abhinav.agentic_ai_chatbot.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}