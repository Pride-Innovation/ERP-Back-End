# Title API Documentation

## Overview

This document covers all title-related requests for the frontend team.

Available title endpoints:

- `GET /api/v1/titles` for paginated listing with filters
- `GET /api/v1/titles/{id}` for single title details
- `POST /api/v1/titles` for creating a title
- `PUT /api/v1/titles/{id}` for updating a title
- `DELETE /api/v1/titles/{id}` for deleting a title

All title endpoints require:

- `Authorization: Bearer <jwt-token>`

## Title List Endpoint

- Method: `GET`
- URL: `/api/v1/titles`
- Response type: Spring `Page<Title>`

### Query Parameters

All query parameters are optional.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `pageNumber` | number | `0` | Zero-based page index. First page is `0`. |
| `pageSize` | number | `10` | Number of records returned per page. |
| `sortBy` | string | `id` | Field used for sorting. Recommended values: `id`, `name`, `shortCode`. |
| `sortDirection` | string | `ASC` | Sort direction. Allowed values: `ASC`, `DESC`. |
| `name` | string | none | Case-insensitive partial match on title name. |
| `shortCode` | string | none | Case-insensitive partial match on title short code. |
| `reportsToId` | number | none | Exact match on parent title ID. |
| `roleId` | number | none | Exact match on role ID. |

### Filter Behavior

- Filters can be combined in the same request.
- All supplied filters are combined with `AND`.
- `name` uses a case-insensitive `contains` match.
- `shortCode` uses a case-insensitive `contains` match.
- `reportsToId` uses exact equality on the parent title ID.
- `roleId` uses exact equality on the assigned role ID.
- If no filters are supplied, the API returns all titles with pagination.

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
- Use valid title entity fields only for `sortBy`.

### Example Requests

#### First page with defaults

```http
GET /api/v1/titles
Authorization: Bearer <jwt-token>
```

#### Second page with page size 20

```http
GET /api/v1/titles?pageNumber=1&pageSize=20
Authorization: Bearer <jwt-token>
```

#### Search by title name

```http
GET /api/v1/titles?name=manager
Authorization: Bearer <jwt-token>
```

#### Filter by short code

```http
GET /api/v1/titles?shortCode=BM
Authorization: Bearer <jwt-token>
```

#### Filter by parent title and role

```http
GET /api/v1/titles?reportsToId=4&roleId=2
Authorization: Bearer <jwt-token>
```

#### Combined filters with sorting and pagination

```http
GET /api/v1/titles?pageNumber=0&pageSize=10&sortBy=name&sortDirection=ASC&name=manager&roleId=2
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "content": [
    {
      "id": 12,
      "name": "Branch Manager",
      "shortCode": "BM",
      "branch": null,
      "reportsTo": {
        "id": 4,
        "name": "Regional Manager",
        "shortCode": "RM"
      },
      "role": {
        "id": 2,
        "name": "BRANCH_MANAGER",
        "description": "Branch manager role",
        "permissions": [
          {
            "id": 11,
            "name": "READ_USER"
          },
          {
            "id": 12,
            "name": "UPDATE_USER"
          }
        ]
      }
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
  "totalPages": 3,
  "totalElements": 23,
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

## Single Title Endpoint

- Method: `GET`
- URL: `/api/v1/titles/{id}`
- Response type: `Title`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Title ID |

### Example Request

```http
GET /api/v1/titles/12
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "id": 12,
  "name": "Branch Manager",
  "shortCode": "BM",
  "branch": null,
  "reportsTo": {
    "id": 4,
    "name": "Regional Manager",
    "shortCode": "RM"
  },
  "role": {
    "id": 2,
    "name": "BRANCH_MANAGER",
    "description": "Branch manager role",
    "permissions": [
      {
        "id": 11,
        "name": "READ_USER"
      }
    ]
  }
}
```

## Create Title Endpoint

- Method: `POST`
- URL: `/api/v1/titles`
- Request body type: `TitleRequestDTO`
- Response type: `Title`

### Request Body

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | Yes | Title name |
| `shortCode` | string | Yes | Title short code |
| `reportsTo` | number or `null` | No | Parent title ID. Use `null` for a top-level title. |
| `role` | number | Yes | Role ID |

### Validation Rules

- `name` is required and cannot be blank.
- `shortCode` is required and cannot be blank.
- `role` is required.
- `reportsTo` is optional.
- If `reportsTo` is supplied, it must reference an existing title.
- `role` must reference an existing role.
- The combination of `name + shortCode + reportsTo` must be unique.

### Functional Note

- The current title API does not accept `branch` in the request body.
- Even though `branch` can appear in title responses, this API does not create or update branch assignment for titles.

### Example Request

```http
POST /api/v1/titles
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Branch Manager",
  "shortCode": "BM",
  "reportsTo": 4,
  "role": 2
}
```

### Example Success Response

```json
{
  "id": 12,
  "name": "Branch Manager",
  "shortCode": "BM",
  "branch": null,
  "reportsTo": {
    "id": 4,
    "name": "Regional Manager",
    "shortCode": "RM"
  },
  "role": {
    "id": 2,
    "name": "BRANCH_MANAGER",
    "description": "Branch manager role",
    "permissions": []
  }
}
```

## Update Title Endpoint

- Method: `PUT`
- URL: `/api/v1/titles/{id}`
- Request body type: `TitleRequestDTO`
- Response type: `Title`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Title ID |

### Request Body

Use the same request body shape as create.

```json
{
  "name": "Branch Manager",
  "shortCode": "BM",
  "reportsTo": 4,
  "role": 2
}
```

### Update Notes

- This is a full update request, not a partial patch.
- The frontend should send the full title payload.
- Required fields must still be present during update.
- `reportsTo` can be set to `null` to make the title top-level.
- A title cannot report to itself.
- A title cannot report to one of its own subordinates.
- The combination of `name + shortCode + reportsTo` must remain unique.

### Example Request

```http
PUT /api/v1/titles/12
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Senior Branch Manager",
  "shortCode": "SBM",
  "reportsTo": 4,
  "role": 2
}
```

### Example Success Response

```json
{
  "id": 12,
  "name": "Senior Branch Manager",
  "shortCode": "SBM",
  "branch": null,
  "reportsTo": {
    "id": 4,
    "name": "Regional Manager",
    "shortCode": "RM"
  },
  "role": {
    "id": 2,
    "name": "BRANCH_MANAGER",
    "description": "Branch manager role",
    "permissions": []
  }
}
```

## Delete Title Endpoint

- Method: `DELETE`
- URL: `/api/v1/titles/{id}`
- Response type: `DeleteResponse`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Title ID |

### Example Request

```http
DELETE /api/v1/titles/12
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "message": "Deleted successfully",
  "success": true
}
```

## Shared Response Shapes

### Title object

| Field | Type | Notes |
|---|---|---|
| `id` | number | Title ID |
| `name` | string | Title name |
| `shortCode` | string | Title short code |
| `branch` | object or `null` | Branch assigned to the title, if present in the database |
| `reportsTo` | object or `null` | Parent title |
| `role` | object | Assigned role |

### Branch object in title responses

| Field | Type | Notes |
|---|---|---|
| `id` | number | Branch ID |
| `name` | string | Branch name |
| `telephone` | string | Branch phone number |
| `email` | string | Branch email |
| `shortCode` | string or `null` | Branch short code |
| `region` | object or `null` | Region details |
| `district` | object or `null` | District details |

### Parent title object in title responses

| Field | Type | Notes |
|---|---|---|
| `id` | number | Parent title ID |
| `name` | string | Parent title name |
| `shortCode` | string | Parent title short code |
| `branch` | object or `null` | May be present |
| `reportsTo` | object or `null` | May be present |
| `role` | object | May be present |

### Role object in title responses

| Field | Type | Notes |
|---|---|---|
| `id` | number | Role ID |
| `name` | string | Role name |
| `description` | string or `null` | Role description |
| `createDate` | string or `null` | Date-time |
| `lastModified` | string or `null` | Date-time |
| `createdBy` | number or `null` | Audit field |
| `lastModifiedBy` | number or `null` | Audit field |
| `permissions` | array | Permission list |

### Permission object in role responses

| Field | Type |
|---|---|
| `id` | number |
| `name` | string |

### DeleteResponse shape

| Field | Type |
|---|---|
| `message` | string |
| `success` | boolean |

## Error Handling

Typical non-success responses:

- `400 Bad Request`: validation failure, duplicate title, or invalid reporting hierarchy
- `401 Unauthorized`: missing or invalid token
- `403 Forbidden`: authenticated user lacks permission
- `404 Not Found`: referenced title or role does not exist
- `500 Internal Server Error`: unexpected backend failure

### Example Validation Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation error",
  "instance": "/api/v1/titles",
  "properties": {
    "name": "Name is required",
    "shortCode": "Short code is required",
    "role": "Role is required"
  }
}
```

### Example Business Rule Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "A title cannot report to itself",
  "request_failure": "Bad Request"
}
```

### Example Duplicate Title Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "A title with name 'Branch Manager' and short code 'BM' already exists",
  "request_failure": "Bad Request"
}
```

### Example Not Found Response

```json
{
  "message": "Title with ID 12 not found"
}
```

## Recommended Frontend Integration

### For listing pages

- Use the list endpoint for the table/grid.
- Keep filters, sorting, and pagination state in the URL if possible.
- Reset `pageNumber` to `0` whenever filters change.
- Read title rows from `content`.
- Read `totalElements` and `totalPages` for pagination controls.

### For create and update forms

- Use a text input for `name`.
- Use a text input for `shortCode`.
- Use a dropdown for `role`.
- Use a dropdown for `reportsTo`, with an option for no parent title.
- Send the complete payload on update.
- Do not send `branch` in the title request body because the current API does not accept it.

### For hierarchy UX

- Prevent selecting the same title as its own parent.
- If possible, prevent selecting known child titles as the parent in the UI.
- Handle backend validation anyway, because the API enforces hierarchy rules server-side.

### For delete flows

- Show a confirmation dialog before delete.
- On success, remove the row locally or refetch the current page.

## Frontend Implementation Checklist

- Send only non-empty filters to the list endpoint.
- Convert UI page `1` to API `pageNumber=0`.
- Preserve filters and sort while changing pages.
- Treat `reportsTo` and `branch` as nullable.
- Expect nested `role` data including permissions in title responses.
- Handle validation errors using the `properties` object in the 400 response.
- Handle business-rule failures using the `detail` field in the 400 response.
- Handle not-found responses using the `message` field.