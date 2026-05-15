# Region API Documentation

## Overview

This document covers all region-related requests for the frontend team.

Available region endpoints:

- `GET /api/v1/regions` for paginated listing with filters
- `GET /api/v1/regions/{id}` for single region details
- `POST /api/v1/regions` for creating a region
- `PUT /api/v1/regions/{id}` for updating a region
- `DELETE /api/v1/regions/{id}` for deleting a region

All region endpoints require:

- `Authorization: Bearer <jwt-token>`

---

## Region List Endpoint

- Method: `GET`
- URL: `/api/v1/regions`
- Response type: Spring `Page<Region>`

### Query Parameters

All query parameters are optional.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `pageNumber` | number | `0` | Zero-based page index. First page is `0`. |
| `pageSize` | number | `10` | Number of records returned per page. |
| `sortBy` | string | `id` | Field used for sorting. Recommended values: `id`, `name`. |
| `sortDirection` | string | `ASC` | Sort direction. Allowed values: `ASC`, `DESC`. |
| `name` | string | none | Case-insensitive partial match on region name. |

### Filter Behavior

- The `name` filter uses a case-insensitive `contains` match.
- If no filters are supplied, the API returns all regions with pagination.

### Pagination Behavior

- Pagination is server-side.
- `pageNumber` is zero-based.
- `pageSize` controls the size of the `content` array.
- Use `totalElements` and `totalPages` from the response to build pagination controls.
- Use `first`, `last`, `number`, and `numberOfElements` to manage page navigation state.

### Sorting Behavior

- Sorting is server-side.
- Only one sort field is supported per request.
- `sortDirection=ASC` sorts ascending.
- `sortDirection=DESC` sorts descending.
- Use valid region entity fields only for `sortBy`.

### Example Requests

#### First page with defaults

```http
GET /api/v1/regions
Authorization: Bearer <jwt-token>
```

#### Second page with page size 20

```http
GET /api/v1/regions?pageNumber=1&pageSize=20
Authorization: Bearer <jwt-token>
```

#### Search by region name

```http
GET /api/v1/regions?name=central
Authorization: Bearer <jwt-token>
```

#### Combined filter with sorting and pagination

```http
GET /api/v1/regions?pageNumber=0&pageSize=10&sortBy=name&sortDirection=ASC&name=central
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "content": [
    {
      "id": 1,
      "name": "Central A"
    },
    {
      "id": 2,
      "name": "Central B"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": {
      "empty": false,
      "sorted": true,
      "unsorted": false
    },
    "offset": 0,
    "paged": true,
    "unpaged": false
  },
  "totalPages": 2,
  "totalElements": 14,
  "last": false,
  "size": 10,
  "number": 0,
  "sort": {
    "empty": false,
    "sorted": true,
    "unsorted": false
  },
  "first": true,
  "numberOfElements": 10,
  "empty": false
}
```

---

## Single Region Endpoint

- Method: `GET`
- URL: `/api/v1/regions/{id}`
- Response type: `Region`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Region ID |

### Example Request

```http
GET /api/v1/regions/1
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "id": 1,
  "name": "Central A"
}
```

---

## Create Region Endpoint

- Method: `POST`
- URL: `/api/v1/regions`
- Request body type: `RegionRequestDTO`
- Response type: `Region`

### Request Body

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | Yes | Region name. Must be unique (case-insensitive). |

### Validation Rules

- `name` is required and cannot be blank.
- `name` must be unique across all regions. A duplicate name is rejected with `400`.

### Example Request

```http
POST /api/v1/regions
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Central A"
}
```

### Example Success Response

HTTP `201 Created`

```json
{
  "id": 1,
  "name": "Central A"
}
```

---

## Update Region Endpoint

- Method: `PUT`
- URL: `/api/v1/regions/{id}`
- Request body type: `RegionRequestDTO`
- Response type: `Region`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Region ID |

### Request Body

Use the same request body shape as create.

```json
{
  "name": "Central A Updated"
}
```

### Update Notes

- This is a full update, not a partial patch.
- `name` is required and must still be present during update.
- The new name must not already be used by a different region.

### Example Request

```http
PUT /api/v1/regions/1
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Central A Updated"
}
```

### Example Success Response

HTTP `200 OK`

```json
{
  "id": 1,
  "name": "Central A Updated"
}
```

---

## Delete Region Endpoint

- Method: `DELETE`
- URL: `/api/v1/regions/{id}`
- Response type: `DeleteResponse`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Region ID |

### Example Request

```http
DELETE /api/v1/regions/1
Authorization: Bearer <jwt-token>
```

### Example Success Response

HTTP `200 OK`

```json
{
  "message": "Deleted successfully",
  "success": true
}
```

---

## Shared Response Shapes

### Region object shape

| Field | Type | Notes |
|---|---|---|
| `id` | number | Region ID |
| `name` | string | Region name |

### DeleteResponse shape

| Field | Type |
|---|---|
| `message` | string |
| `success` | boolean |

---

## Error Handling

Typical non-success responses:

- `400 Bad Request`: validation failure or duplicate region name
- `401 Unauthorized`: missing or invalid token
- `403 Forbidden`: authenticated user lacks permission
- `404 Not Found`: region does not exist
- `409 Conflict`: data integrity violation (e.g. region is still referenced by branches)
- `500 Internal Server Error`: unexpected backend failure

### Example Validation Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation error",
  "instance": "/api/v1/regions",
  "properties": {
    "name": "Region name is required"
  }
}
```

### Example Duplicate Name Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "A region with the name 'Central A' already exists",
  "instance": "/api/v1/regions"
}
```

### Example Not Found Response

```json
{
  "message": "Region with ID 1 not found"
}
```

---

## Recommended Frontend Integration

### For listing pages

- Use the list endpoint for the table/grid.
- Keep filters, sorting, and pagination state in the URL if possible.
- Reset `pageNumber` to `0` whenever filters change.
- Read region rows from `content`.
- Read `totalElements` and `totalPages` for pagination controls.
- Regions are also useful as a filter input on the branch listing page (`regionId` filter).

### For create and update forms

- The only required field is `name`.
- After create or update, either refetch the region list or call the single-region endpoint.

### For delete flows

- Show a confirmation dialog before delete.
- Be aware that deleting a region that still has branches associated with it will cause a `409 Conflict` data integrity error.
- On success, remove the row locally or refetch the current page.

---

## Frontend Implementation Checklist

- Send only non-empty filters to the list endpoint.
- Convert UI page `1` to API `pageNumber=0`.
- Preserve filters and sort while changing pages.
- Handle validation errors using the `properties` object in the `400` response.
- Handle duplicate name errors using the `detail` field in the `400` response.
- Handle not-found responses using the `message` field.
- Handle `409 Conflict` on delete — inform the user that the region cannot be deleted while branches are assigned to it.
