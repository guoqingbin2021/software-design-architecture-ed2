// 反例：把一次开课误当成课程目录中的课程
class Student {
    Set<Course> courses;
}
class Course {
    int capacity;
}
// 反例：检查与写入分开，没有保护共同受竞争数据
if (countActive(classId) < capacity(classId)) {
    insertEnrollment(studentId, classId);
}
