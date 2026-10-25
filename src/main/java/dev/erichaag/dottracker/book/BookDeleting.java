package dev.erichaag.dottracker.book;

import java.util.UUID;

/**
 * Published by {@link BookService#delete} before it deletes a book, inside the same transaction. A listener in another
 * domain refuses the delete by throwing, which rolls it back, so this package never has to know about that domain.
 */
public record BookDeleting(UUID bookId) {
}
