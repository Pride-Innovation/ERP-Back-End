# Department API Documentation

## Overview

This document covers all department-related requests for the frontend team.

Available department endpoints:

- `GET /api/v1/departments` for paginated listing with filters
- `GET /api/v1/departments/{id}` for single department details
- `POST /api/v1/departments` for creating a department
- `PUT /api/v1/departments/{id}` for updating a department
- `DELETE /api/v1/departments/{id}` for deleting a department

All department endpoints require:

- `Authorization: Bearer <jwt-token>`

## Department List Endpoint

- Method: `GET`
- URL: `/api/v1/departments`
- Response type: Spring `Page<Department>`

### Query Parameters

All query parameters are optional.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `pageNumber` | number | `0` | Zero-based page index. First page is `0`. |
| `pageSize` | number | `10` | Number of records returned per page. |
| `sortBy` | string | `id` | Field used for sorting. Recommended values: `id`, `name`, `shortCode`, `managersGroupEmail`. |
| `sortDirection` | string | `ASC` | Sort direction. Allowed values: `ASC`, `DESC`. |
| `name` | string | none | Case-insensitive partial match on department name. |

### Filter Behavior

- The `name` filter uses a case-insensitive `contains` match.
- If no filters are supplied, the API returns all departments with pagination.

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
- Use valid department entity fields only for `sortBy`.

### Example Requests

#### First page with defaults

```http
GET /api/v1/departments
Authorization: Bearer <jwt-token>
```

#### Second page with page size 20

```http
GET /api/v1/departments?pageNumber=1&pageSize=20
Authorization: Bearer <jwt-token>
```

#### Search by department name

```http
GET /api/v1/departments?name=finance
Authorization: Bearer <jwt-token>
```

#### Combined filter with sorting and pagination

```http
GET /api/v1/departments?pageNumber=0&pageSize=10&sortBy=name&sortDirection=ASC&name=operations
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "content": [
    {
      "id": 3,
      "name": "Finance Department",
      "managersGroupEmail": "finance.managers@pridebank.co.ug",
      "shortCode": "FIN",
      "headOfDepartment": {
        "id": 8,
        "firstName": "Alice",
        "lastName": "Nabwire",
        "email": "alice.nabwire@pridebank.co.ug"
      },
      "branch": {
        "id": 12,
        "name": "Kampala Main Branch",
        "email": "kampala.main@pridebank.co.ug",
        "telephone": "+256700000000",
        "shortCode": "KMP"
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

## Single Department Endpoint

- Method: `GET`
- URL: `/api/v1/departments/{id}`
- Response type: `Department`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Department ID |

### Example Request

```http
GET /api/v1/departments/3
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "id": 3,
  "name": "Finance Department",
  "managersGroupEmail": "finance.managers@pridebank.co.ug",
  "shortCode": "FIN",
  "headOfDepartment": {
    "id": 8,
    "firstName": "Alice",
    "lastName": "Nabwire",
    "email": "alice.nabwire@pridebank.co.ug"
  },
  "branch": {
    "id": 12,
    "name": "Kampala Main Branch",
    "email": "kampala.main@pridebank.co.ug",
    "telephone": "+256700000000",
    "shortCode": "KMP"
  }
}
```

## Create Department Endpoint

- Method: `POST`
- URL: `/api/v1/departments`
- Request body type: `DepartmentDTO`
- Response type: `Department`

### Request Body

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | Yes | Department name. Must be unique. |
| `headOfDepartment` | number | Yes | User ID of the head of department. |
| `branch` | number | Yes | Branch ID the department belongs to. |
| `managersGroupEmail` | string | No | Must be a valid email address when provided. |

### Validation Rules

- `name` is required and cannot be blank. Must be unique across all departments.
- `headOfDepartment` is required and must reference an existing user.
- `branch` is required and must reference an existing branch.
- `managersGroupEmail` is optional. When provided, it must be a valid email address.

### Example Request

```http
POST /api/v1/departments
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Finance Department",
  "headOfDepartment": 8,
  "branch": 12,
  "managersGroupEmail": "finance.managers@pridebank.co.ug"
}
```

### Example Success Response

`201 Created`

```json
{
  "id": 3,
  "name": "Finance Department",
  "managersGroupEmail": "finance.managers@pridebank.co.ug",
  "shortCode": null,
  "headOfDepartment": {
    "id": 8,
    "firstName": "Alice",
    "lastName": "Nabwire",
    "email": "alice.nabwire@pridebank.co.ug"
  },
  "branch": {
    "id": 12,
    "name": "Kampala Main Branch",
    "email": "kampala.main@pridebank.co.ug",
    "telephone": "+256700000000",
    "shortCode": "KMP"
  }
}
```

## Update Department Endpoint

- Method: `PUT`
- URL: `/api/v1/departments/{id}`
- Request body type: `DepartmentDTO`
- Response type: `Department`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Department ID |

### Request Body

Use the same request body shape as create.

```json
{
  "name": "Finance Department",
  "headOfDepartment": 8,
  "branch": 12,
  "managersGroupEmail": "finance.managers@pridebank.co.ug"
}
```

### Update Notes

- This is a full update request, not a partial patch.
- The frontend should send the full department payload.
- Required fields must still be present during update.
- If the name is changed, it must remain unique across all departments.

### Example Request

```http
PUT /api/v1/departments/3
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Finance & Accounting Department",
  "headOfDepartment": 8,
  "branch": 12,
  "managersGroupEmail": "finance.managers@pridebank.co.ug"
}
```

### Example Success Response

`200 OK`

```json
{
  "id": 3,
  "name": "Finance & Accounting Department",
  "managersGroupEmail": "finance.managers@pridebank.co.ug",
  "shortCode": "FIN",
  "headOfDepartment": {
    "id": 8,
    "firstName": "Alice",
    "lastName": "Nabwire",
    "email": "alice.nabwire@pridebank.co.ug"
  },
  "branch": {
    "id": 12,
    "name": "Kampala Main Branch",
    "email": "kampala.main@pridebank.co.ug",
    "telephone": "+256700000000",
    "shortCode": "KMP"
  }
}
```

## Delete Department Endpoint

- Method: `DELETE`
- URL: `/api/v1/departments/{id}`
- Response type: `DeleteResponse`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Department ID |

### Example Request

```http
DELETE /api/v1/departments/3
Authorization: Bearer <jwt-token>
```

### Example Success Response

`200 OK`

```json
{
  "message": "Deleted successfully",
  "success": true
}
```

## Shared Response Shapes

### Department object shape

| Field | Type | Notes |
|---|---|---|
| `id` | number | Department ID |
| `name` | string | Department name |
| `managersGroupEmail` | string or `null` | Email address for the managers group |
| `shortCode` | string or `null` | Short identifier code for the department |
| `headOfDepartment` | object | Embedded user object (see below) |
| `branch` | object | Embedded branch object (see below) |

### Head of department object shape

| Field | Type |
|---|---|
| `id` | number |
| `firstName` | string |
| `lastName` | string |
| `email` | string |

### Branch object shape (embedded in department)

| Field | Type |
|---|---|
| `id` | number |
| `name` | string |
| `email` | string |
| `telephone` | string |
| `shortCode` | string or `null` |

### DeleteResponse shape

| Field | Type |
|---|---|
| `message` | string |
| `success` | boolean |

## Error Handling

Typical non-success responses:

- `400 Bad Request`: validation failure or duplicate department name
- `401 Unauthorized`: missing or invalid token
- `403 Forbidden`: authenticated user lacks permission
- `404 Not Found`: referenced department, user, or branch does not exist
- `409 Conflict`: data integrity violation
- `500 Internal Server Error`: unexpected backend failure

### Example Validation Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation error",
  "instance": "/api/v1/departments",
  "properties": {
    "name": "Department name is required",
    "headOfDepartment": "Head of department is required",
    "branch": "Branch is required",
    "managersGroupEmail": "Email address must be valid"
  }
}
```

### Example Duplicate Name Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "A department with the name 'Finance Department' already exists",
  "instance": "/api/v1/departments"
}
```

### Example Not Found Response

```json
{
  "message": "Department with Id 3 is not found"
}
```
