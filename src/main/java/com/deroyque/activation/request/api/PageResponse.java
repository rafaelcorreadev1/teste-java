package com.deroyque.activation.request.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/** Contrato próprio de página, para não expor a serialização interna do {@code Page} do Spring. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
