package com.tip.data.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EmployeeServiceTest {

    @Autowired
    private EmployeeService employeeService;

    @Test
    void addEmployee_shouldPersistEmployee_andReturnWithId() {
        // given
        Employee e = new Employee();
        e.setName("Mahitha");

        // when
        Employee saved = employeeService.addEmployee(e);

        // then
        assertThat(saved.getId()).isNotNull();     // DB generated id
        assertThat(saved.getName()).isEqualTo("Mahitha");
    }
}
