package com.hr.evaluation.domain;

public enum QuestionType {
    TEXT,              // Single line
    PARAGRAPH,         // Multi-line text
    MULTIPLE_CHOICE,   // Radio buttons
    CHECKBOX,          // Multiple selections
    RATING,            // Star rating (1-5)
    SCALE,             // Numeric scale (1-10)
    DATE,              // Date picker
    NUMBER             // Numeric input
}
