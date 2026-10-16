package com.ecomerceshopeonline.ecomercewepapp.service.impl;

import com.ecomerceshopeonline.ecomercewepapp.dto.request.MoveDirection;
import com.ecomerceshopeonline.ecomercewepapp.exception.BadRequestException;
import com.ecomerceshopeonline.ecomercewepapp.exception.ResourceNotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Move up / move down for rows the admin puts in order: an int sortOrder (lower first)
 * followed by a unique tiebreaker, e.g. products (newest first) and categories (by name).
 */
final class DisplayOrder {

    private DisplayOrder() {
    }

    /**
     * Swaps the row with its neighbour in the display order.
     *
     * @param rows   [id, sortOrder] of every row, in display order - first row first
     * @param entity what the rows are ("Product", "Category"), for the error messages
     * @param update writes one row's new sortOrder
     * @throws ResourceNotFoundException when {@code id} is not among the rows
     * @throws BadRequestException       when the row is already first (UP) or last (DOWN)
     */
    static void move(List<Object[]> rows, Long id, MoveDirection direction, String entity,
                     BiConsumer<Long, Integer> update) {
        List<Long> ids = new ArrayList<>(rows.size());
        Map<Long, Integer> sortOrders = new HashMap<>();
        for (Object[] row : rows) {
            Long rowId = ((Number) row[0]).longValue();
            ids.add(rowId);
            sortOrders.put(rowId, ((Number) row[1]).intValue());
        }

        int from = ids.indexOf(id);
        if (from < 0) {
            throw ResourceNotFoundException.of(entity, id);
        }
        int to = direction == MoveDirection.UP ? from - 1 : from + 1;
        if (to < 0 || to >= ids.size()) {
            throw new BadRequestException("This " + entity.toLowerCase(Locale.ROOT) + " is already "
                    + (direction == MoveDirection.UP ? "first" : "last"));
        }
        Long neighbourId = ids.get(to);

        // Sorted by id so the row locks these updates take come in ascending id order - the
        // same global order checkout and cancellation lock product rows in, which keeps a move
        // and a concurrent order from deadlocking on each other.
        Map<Long, Integer> writes = new TreeMap<>();
        if (new HashSet<>(sortOrders.values()).size() == ids.size()) {
            // Every value is distinct, so trading the two values swaps exactly these neighbours.
            writes.put(id, sortOrders.get(neighbourId));
            writes.put(neighbourId, sortOrders.get(id));
        } else {
            // Tied values (every row from before ordering existed starts at 0) are ordered by the
            // tiebreaker alone, so trading values could jump past a tied neighbour. Renumber the
            // whole list once instead - only rows whose value changes are written, and later
            // moves take the two-row path above.
            Collections.swap(ids, from, to);
            for (int position = 0; position < ids.size(); position++) {
                Long rowId = ids.get(position);
                if (sortOrders.get(rowId) != position) {
                    writes.put(rowId, position);
                }
            }
        }
        writes.forEach(update);
    }
}
