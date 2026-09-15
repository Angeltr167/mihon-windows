-- Mihon Windows port Phase 0 database compatibility fixture.
-- Intended to be loaded after the current SQLDelight schema is created.
-- Values exercise manga/chapter identity, booleans, progress, notes, timestamps,
-- version fields, update strategy, and JSON-object memo blobs.

PRAGMA foreign_keys = ON;

INSERT INTO mangas(
    _id,
    source,
    url,
    artist,
    author,
    description,
    genre,
    title,
    status,
    thumbnail_url,
    favorite,
    last_update,
    next_update,
    initialized,
    viewer,
    chapter_flags,
    cover_last_modified,
    date_added,
    update_strategy,
    calculate_interval,
    last_modified_at,
    favorite_modified_at,
    version,
    is_syncing,
    notes,
    memo
) VALUES (
    1,
    1000001,
    '/fixture/manga/one',
    'Fixture Artist',
    'Fixture Author',
    'Cross-platform database compatibility fixture',
    NULL,
    'Fixture Manga',
    1,
    'https://example.invalid/fixture-cover.jpg',
    1,
    1700000000000,
    1700086400000,
    1,
    0,
    0,
    1700000000000,
    1690000000000,
    0,
    0,
    1700000000000,
    1700000000000,
    7,
    0,
    'phase-0 fixture',
    X'7B7D'
);

INSERT INTO chapters(
    _id,
    manga_id,
    url,
    name,
    scanlator,
    read,
    bookmark,
    last_page_read,
    chapter_number,
    source_order,
    date_fetch,
    date_upload,
    last_modified_at,
    version,
    is_syncing,
    memo
) VALUES (
    1,
    1,
    '/fixture/manga/one/chapter-1',
    'Chapter 1',
    'Fixture Scanlator',
    0,
    1,
    12,
    1.0,
    0,
    1700000000000,
    1699990000000,
    1700000000000,
    3,
    0,
    X'7B7D'
);

-- Invariants expected after import:
--   SELECT title, favorite, version FROM mangas WHERE _id = 1;
--   => Fixture Manga | 1 | 7
--   SELECT name, bookmark, last_page_read, version FROM chapters WHERE _id = 1;
--   => Chapter 1 | 1 | 12 | 3
