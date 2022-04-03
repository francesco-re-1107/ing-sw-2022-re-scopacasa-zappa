package it.polimi.ingsw.model;

import it.polimi.ingsw.Utils;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * This class represents a generic students container which cannot be modified
 */
public abstract class AStudentsContainer {

    /**
     * Used internally to store the students
     */
    protected final Map<Student, Integer> students;

    /**
     * Used internally to store the maximum number of values that this container can hold
     */
    protected int maxSize = Integer.MAX_VALUE;

    /**
     * Create a generic container (and not modifiable) with given maxSize
     */
    public AStudentsContainer() {
        this.students = new HashMap<>();
    }

    /**
     * Create a generic container (and not modifiable) with given maxSize
     * @param maxSize maximum number of students that this container can hold
     */
    public AStudentsContainer(int maxSize) {
        this();
        this.maxSize = maxSize;
    }


    /**
     * @return the number of students (every color) held in this container
     */
    public int getSize(){
        return students
                .values()
                .stream()
                .mapToInt(a -> a)
                .sum();
    }

    /**
     * Set the maximum number of students that this container can hold, if not set it is Integer.MAX_VALUE
     * @param maxSize
     */
    public void setMaxSize(int maxSize){
        this.maxSize = maxSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    /**
     * @return a copy of the private map used for storing students
     */
    public Map<Student, Integer> getStudents() {
        return new HashMap<>(students);
    }

    /**
     * @param student the student used for filtering
     * @return the number of students (of type student) held in this container
     */
    public int getCountForStudent(Student student) {
        return Utils.nullAlternative(students.get(student), 0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AStudentsContainer that = (AStudentsContainer) o;
        return maxSize == that.maxSize && students.equals(that.students);
    }
}
