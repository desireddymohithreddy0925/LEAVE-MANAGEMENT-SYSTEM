package com.leave_management_system.leave_management_system.entity;

public enum LeaveStatus {
    // enumeration special data type used to define a collection of named constants. It acts as a specialized class that represents a fixed set of predefined, unchanging variables (like days of the week, compass directions, or traffic light colors)

    // "Leave request has been submitted but not reviewed yet."
    PENDING,

    // "Manager has approved the leave request."
    APPROVED,

    // "Manager has rejected the leave request."
    REJECTED,

    // "Leave request has been cancelled by the employee."
    CANCELLED
}