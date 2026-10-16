package com.ecomerceshopeonline.ecomercewepapp.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigDecimal;

/**
 * The shop's own star score in half stars: 0.5, 1.0, 1.5 ... 5.0. Null ("unchanged") and 0
 * ("remove the shop rating") are accepted too; anything else - 0.3, 4.25, 5.5, negatives - is not.
 */
@Documented
@Constraint(validatedBy = ShopRatingValue.Validator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ShopRatingValue {

    String message() default "Shop rating must be between 0.5 and 5 in steps of 0.5";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ShopRatingValue, BigDecimal> {

        private static final BigDecimal MIN = new BigDecimal("0.5");
        private static final BigDecimal MAX = BigDecimal.valueOf(5);
        private static final BigDecimal TWO = BigDecimal.valueOf(2);

        @Override
        public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
            if (value == null || value.signum() == 0) {
                return true;
            }
            if (value.compareTo(MIN) < 0 || value.compareTo(MAX) > 0) {
                return false;
            }
            // A whole number of half stars: twice the value has no fraction left.
            return value.multiply(TWO).stripTrailingZeros().scale() <= 0;
        }
    }
}
