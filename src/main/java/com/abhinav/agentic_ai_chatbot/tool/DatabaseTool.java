package com.abhinav.agentic_ai_chatbot.tool;

import com.abhinav.agentic_ai_chatbot.dto.ToolResult;
import com.abhinav.agentic_ai_chatbot.entity.Employee;
import com.abhinav.agentic_ai_chatbot.service.DatabaseService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseTool {

    private final DatabaseService databaseService;

    public DatabaseTool(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public ToolResult getEmployeeDetails(Long employeeId) {

        Employee employee =
                databaseService.getEmployeeDetails(employeeId);

        if (employee == null) {

            return new ToolResult(
                    "DATABASE",
                    false,
                    "No employee was found with ID " + employeeId + ".",
                    null
            );
        }

        return new ToolResult(
                "DATABASE",
                true,
                "Employee information retrieved successfully.",
                employee
        );
    }

    public ToolResult getEmployeeDetailsByName(String name) {

        Employee employee =
                databaseService.getEmployeeDetailsByName(name);

        if (employee == null) {

            return new ToolResult(
                    "DATABASE",
                    false,
                    "No employee was found with name " + name + ".",
                    null
            );
        }

        return new ToolResult(
                "DATABASE",
                true,
                "Employee information retrieved successfully.",
                employee
        );
    }

    public ToolResult getEmployeesByDepartment(String department) {

        List<Employee> employees =
                databaseService.getEmployeesByDepartment(department);

        if (employees == null || employees.isEmpty()) {

            return new ToolResult(
                    "DATABASE",
                    false,
                    "No employees were found in department "
                            + department + ".",
                    List.of()
            );
        }

        return new ToolResult(
                "DATABASE",
                true,
                "Employees retrieved successfully.",
                employees
        );
    }

    public ToolResult getEmployeesByLocation(String location) {

        List<Employee> employees =
                databaseService.getEmployeesByLocation(location);

        if (employees == null || employees.isEmpty()) {

            return new ToolResult(
                    "DATABASE",
                    false,
                    "No employees were found in location "
                            + location + ".",
                    List.of()
            );
        }

        return new ToolResult(
                "DATABASE",
                true,
                "Employees retrieved successfully.",
                employees
        );
    }

    public ToolResult getEmployeesByRole(String role) {

        List<Employee> employees =
                databaseService.getEmployeesByRole(role);

        if (employees == null || employees.isEmpty()) {

            return new ToolResult(
                    "DATABASE",
                    false,
                    "No employees were found for role "
                            + role + ".",
                    List.of()
            );
        }

        return new ToolResult(
                "DATABASE",
                true,
                "Employees retrieved successfully.",
                employees
        );
    }
}