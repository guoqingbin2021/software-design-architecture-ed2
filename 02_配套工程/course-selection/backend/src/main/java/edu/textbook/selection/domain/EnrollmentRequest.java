package edu.textbook.selection.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/** Immutable success snapshot: replaying this record never mutates an Enrollment. */
@Entity @Table(name="enrollment_request")
public class EnrollmentRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="student_id",nullable=false) private Long studentId;
    @Column(name="request_key",nullable=false,length=80) private String requestKey;
    @Column(name="class_id",nullable=false) private Long classId;
    @Column(name="enrollment_id",nullable=false) private Long enrollmentId;
    @JdbcTypeCode(SqlTypes.LONGVARCHAR) @Column(name="response_json",nullable=false,columnDefinition="TEXT") private String responseJson;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    protected EnrollmentRequest() {}
    public static EnrollmentRequest completed(long studentId,String key,long classId,long enrollmentId,String snapshot,Instant now) {
        var record=new EnrollmentRequest();
        record.studentId=studentId; record.requestKey=key; record.classId=classId;
        record.enrollmentId=enrollmentId; record.responseJson=snapshot;
        record.createdAt=now;
        return record;
    }
    public Long getClassId() { return classId; }
    public String getResponseJson() { return responseJson; }
}
