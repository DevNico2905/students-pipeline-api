package co.edu.unisalle.students.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record Student(
        Long id,
        @NotBlank String name,
        @NotBlank @Email String email,
        @NotBlank String program
) {
    public Student withId(Long newId) {
        return new Student(newId, name, email, program);
    }
}
