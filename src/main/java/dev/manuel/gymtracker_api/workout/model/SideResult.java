package dev.manuel.gymtracker_api.workout.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class SideResult {
    @Min(1)
    private Integer repetitions;
    @Min(1)
    private Integer durationSeconds;
    @DecimalMin("0") @DecimalMax("9999.99") @Digits(integer = 4, fraction = 2)
    @Column(precision = 6, scale = 2)
    private BigDecimal additionalWeightKg;
    @NotNull
    private Boolean completed;
}
