package dev.iury.lifeos.finance.common;

import java.math.BigDecimal;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MoneyAttributeConverter implements AttributeConverter<BigDecimal, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(BigDecimal value) {
        return value == null ? null : Money.scale(value);
    }

    @Override
    public BigDecimal convertToEntityAttribute(BigDecimal value) {
        return value == null ? null : Money.scale(value);
    }
}
