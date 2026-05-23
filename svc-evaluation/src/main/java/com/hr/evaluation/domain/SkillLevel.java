package com.hr.evaluation.domain;

public enum SkillLevel {
    DEBUTANT(1, "Beginner"),
    SUPERVISE(2, "Supervised"),
    INTERMEDIAIRE(2, "Supervised"),
    AUTONOME(3, "Autonomous"),
    AVANCE(4, "Advanced"),
    EXPERT(5, "Expert");

    private final int score;
    private final String label;

    SkillLevel(int score, String label) {
        this.score = score;
        this.label = label;
    }

    public int score() {
        return score;
    }

    public String label() {
        return label;
    }
}
