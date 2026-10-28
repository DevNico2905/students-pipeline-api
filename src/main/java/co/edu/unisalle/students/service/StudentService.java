package co.edu.unisalle.students.service;

import co.edu.unisalle.students.model.Student;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Almacenamiento en memoria: suficiente para demostrar el pipeline sin depender de una BD. */
@Service
public class StudentService {

    private final Map<Long, Student> store = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public StudentService() {
        create(new Student(null, "Ana Gómez", "ana.gomez@unisalle.edu.co", "Ingeniería de Software"));
        create(new Student(null, "Carlos Ruiz", "carlos.ruiz@unisalle.edu.co", "Ingeniería de Software"));
    }

    public List<Student> findAll() {
        return new ArrayList<>(store.values());
    }

    public Optional<Student> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public Student create(Student student) {
        Student saved = student.withId(sequence.incrementAndGet());
        store.put(saved.id(), saved);
        return saved;
    }

    public Optional<Student> update(Long id, Student student) {
        if (!store.containsKey(id)) return Optional.empty();
        Student updated = student.withId(id);
        store.put(id, updated);
        return Optional.of(updated);
    }

    public boolean delete(Long id) {
        return store.remove(id) != null;
    }
}
