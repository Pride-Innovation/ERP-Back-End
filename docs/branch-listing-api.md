# Branch API Documentation

## Overview

This document covers all branch-related requests for the frontend team.

Available branch endpoints:

- `GET /api/v1/branches` for paginated listing with filters
- `GET /api/v1/branches/{id}` for single branch details
- `POST /api/v1/branches` for creating a branch
- `PUT /api/v1/branches/{id}` for updating a branch
- `DELETE /api/v1/branches/{id}` for deleting a branch

All branch endpoints require:

- `Authorization: Bearer <jwt-token>`

## Branch List Endpoint

- Method: `GET`
- URL: `/api/v1/branches`
- Response type: Spring `Page<BranchWithManagersDTO>`

### Query Parameters

All query parameters are optional.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `pageNumber` | number | `0` | Zero-based page index. First page is `0`. |
| `pageSize` | number | `10` | Number of records returned per page. |
| `sortBy` | string | `id` | Field used for sorting. Recommended values: `id`, `name`, `email`, `telephone`, `shortCode`. |
| `sortDirection` | string | `ASC` | Sort direction. Allowed values: `ASC`, `DESC`. |
| `name` | string | none | Case-insensitive partial match on branch name. |
| `regionId` | number | none | Exact match on region ID. |
| `districtId` | number | none | Exact match on district ID. |
| `branchManagerId` | number | none | Exact match on branch manager user ID. |
| `branchOperationsManagerId` | number | none | Exact match on branch operations manager user ID. |
| `relationshipManagerId` | number | none | Exact match on relationship manager user ID. |
| `creditAdministratorId` | number | none | Exact match on credit administrator user ID. |

### Filter Behavior

- Filters can be combined in the same request.
- All supplied filters are combined with `AND`.
- `name` uses a case-insensitive `contains` match.
- All ID-based filters use exact equality.
- If no filters are supplied, the API returns all branches with pagination.

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
- Use valid branch entity fields only for `sortBy`.

### Example Requests

#### First page with defaults

```http
GET /api/v1/branches
Authorization: Bearer <jwt-token>
```

#### Second page with page size 20

```http
GET /api/v1/branches?pageNumber=1&pageSize=20
Authorization: Bearer <jwt-token>
```

#### Search by branch name

```http
GET /api/v1/branches?name=kampala
Authorization: Bearer <jwt-token>
```

#### Filter by region and district

```http
GET /api/v1/branches?regionId=1&districtId=3
Authorization: Bearer <jwt-token>
```

#### Filter by managers

```http
GET /api/v1/branches?branchManagerId=5&relationshipManagerId=9
Authorization: Bearer <jwt-token>
```

#### Combined filters with sorting and pagination

```http
GET /api/v1/branches?pageNumber=0&pageSize=10&sortBy=name&sortDirection=ASC&name=kampala&regionId=1
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "content": [
    {
      "id": 12,
      "name": "Kampala Main Branch",
      "email": "kampala.main@pridebank.co.ug",
      "telephone": "+256700000000",
      "branchManager": {
        "id": 5,
        "firstName": "Jane",
        "lastName": "Doe",
        "email": "jane.doe@pridebank.co.ug"
      },
      "branchOperationsManager": {
        "id": 7,
        "firstName": "Peter",
        "lastName": "Okello",
        "email": "peter.okello@pridebank.co.ug"
      },
      "relationshipManager": {
        "id": 9,
        "firstName": "Sarah",
        "lastName": "Achan",
        "email": "sarah.achan@pridebank.co.ug"
      },
      "creditAdministrator": {
        "id": 11,
        "firstName": "David",
        "lastName": "Mukiibi",
        "email": "david.mukiibi@pridebank.co.ug"
      },
      "region": {
        "id": 1,
        "name": "Central A"
      },
      "district": {
        "id": 3,
        "name": "Kampala"
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
  "totalPages": 5,
  "totalElements": 47,
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

## Single Branch Endpoint

- Method: `GET`
- URL: `/api/v1/branches/{id}`
- Response type: `BranchWithManagersDTO`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Branch ID |

### Example Request

```http
GET /api/v1/branches/12
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "id": 12,
  "name": "Kampala Main Branch",
  "email": "kampala.main@pridebank.co.ug",
  "telephone": "+256700000000",
  "branchManager": {
    "id": 5,
    "firstName": "Jane",
    "lastName": "Doe",
    "email": "jane.doe@pridebank.co.ug"
  },
  "branchOperationsManager": {
    "id": 7,
    "firstName": "Peter",
    "lastName": "Okello",
    "email": "peter.okello@pridebank.co.ug"
  },
  "relationshipManager": {
    "id": 9,
    "firstName": "Sarah",
    "lastName": "Achan",
    "email": "sarah.achan@pridebank.co.ug"
  },
  "creditAdministrator": {
    "id": 11,
    "firstName": "David",
    "lastName": "Mukiibi",
    "email": "david.mukiibi@pridebank.co.ug"
  },
  "region": {
    "id": 1,
    "name": "Central A"
  },
  "district": {
    "id": 3,
    "name": "Kampala"
  }
}
```

## Create Branch Endpoint

- Method: `POST`
- URL: `/api/v1/branches`
- Request body type: `BranchDTO`
- Response type: `Branch`

### Request Body

| Field | Type | Required | Notes |
|---|---|---|---|
| `branchManager` | number or `null` | No | User ID |
| `branchOperationsManager` | number or `null` | No | User ID |
| `creditAdministrator` | number or `null` | No | User ID |
| `relationshipManager` | number or `null` | No | User ID |
| `name` | string | Yes | Branch name |
| `region` | number | Yes | Region ID |
| `telephone` | string | Yes | Must match backend phone validation |
| `email` | string | Yes | Must be a valid email address |
| `district` | number | Yes | District ID |

### Validation Rules

- `name` is required and cannot be blank.
- `region` is required.
- `district` is required.
- `telephone` is required and must match the backend pattern for Ugandan phone numbers.
- `email` is required and must be a valid email address.
- Manager IDs are optional, but if supplied they must reference existing users.

### Example Request

```http
POST /api/v1/branches
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "branchManager": 5,
  "branchOperationsManager": 7,
  "creditAdministrator": 11,
  "relationshipManager": 9,
  "name": "Kampala Main Branch",
  "region": 1,
  "telephone": "+256700000000",
  "email": "kampala.main@pridebank.co.ug",
  "district": 3
}
```

### Example Success Response

Note: create returns the `Branch` entity shape, not the richer `BranchWithManagersDTO` shape.

```json
{
  "id": 12,
  "name": "Kampala Main Branch",
  "telephone": "+256700000000",
  "email": "kampala.main@pridebank.co.ug",
  "shortCode": null,
  "region": {
    "id": 1,
    "name": "Central A"
  },
  "district": {
    "id": 3,
    "name": "Kampala"
  }
}
```

## Update Branch Endpoint

- Method: `PUT`
- URL: `/api/v1/branches/{id}`
- Request body type: `BranchDTO`
- Response type: `Branch`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Branch ID |

### Request Body

Use the same request body shape as create.

```json
{
  "branchManager": 5,
  "branchOperationsManager": 7,
  "creditAdministrator": 11,
  "relationshipManager": 9,
  "name": "Kampala Main Branch",
  "region": 1,
  "telephone": "+256700000000",
  "email": "kampala.main@pridebank.co.ug",
  "district": 3
}
```

### Update Notes

- This is a full update request, not a partial patch.
- The frontend should send the full branch payload.
- Required fields must still be present during update.

### Example Request

```http
PUT /api/v1/branches/12
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "branchManager": 5,
  "branchOperationsManager": 7,
  "creditAdministrator": 11,
  "relationshipManager": 9,
  "name": "Kampala Main Branch Updated",
  "region": 1,
  "telephone": "+256700000000",
  "email": "kampala.main@pridebank.co.ug",
  "district": 3
}
```

### Example Success Response

```json
{
  "id": 12,
  "name": "Kampala Main Branch Updated",
  "telephone": "+256700000000",
  "email": "kampala.main@pridebank.co.ug",
  "shortCode": null,
  "region": {
    "id": 1,
    "name": "Central A"
  },
  "district": {
    "id": 3,
    "name": "Kampala"
  }
}
```

## Delete Branch Endpoint

- Method: `DELETE`
- URL: `/api/v1/branches/{id}`
- Response type: `DeleteResponse`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Branch ID |

### Example Request

```http
DELETE /api/v1/branches/12
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

### BranchWithManagersDTO

| Field | Type | Notes |
|---|---|---|
| `id` | number | Branch ID |
| `name` | string | Branch name |
| `email` | string | Branch email |
| `telephone` | string | Branch phone number |
| `branchManager` | object or `null` | Lightweight user details |
| `branchOperationsManager` | object or `null` | Lightweight user details |
| `relationshipManager` | object or `null` | Lightweight user details |
| `creditAdministrator` | object or `null` | Lightweight user details |
| `region` | object or `null` | Includes `id` and `name` |
| `district` | object or `null` | Includes `id` and `name` |

### Manager object shape

| Field | Type |
|---|---|
| `id` | number |
| `firstName` | string |
| `lastName` | string |
| `email` | string |

### Region object shape

| Field | Type |
|---|---|
| `id` | number |
| `name` | string |

### District object shape

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

- `400 Bad Request`: validation failure
- `401 Unauthorized`: missing or invalid token
- `403 Forbidden`: authenticated user lacks permission
- `404 Not Found`: referenced branch, user, region, or district does not exist
- `500 Internal Server Error`: unexpected backend failure

### Example Validation Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation error",
  "instance": "/api/v1/branches",
  "properties": {
    "name": "Branch name is required",
    "telephone": "Invalid Ugandan phone number",
    "email": "Email address must be valid"
  }
}
```

### Example Not Found Response

```json
{
  "message": "Branch with ID 12 not found"
}
```

## Recommended Frontend Integration

### For listing pages

- Use the list endpoint for the table/grid.
- Keep filters, sorting, and pagination state in the URL if possible.
- Reset `pageNumber` to `0` whenever filters change.
- Read branch rows from `content`.
- Read `totalElements` and `totalPages` for pagination controls.

### For create and update forms

- Use dropdowns for `region`, `district`, and all manager fields.
- Store the selected IDs in the request payload.
- Send the complete payload on update.
- After create or update, either refetch the branch list or call the single-branch endpoint.

### For delete flows

- Show a confirmation dialog before delete.
- On success, remove the row locally or refetch the current page.

## Frontend Implementation Checklist

- Send only non-empty filters to the list endpoint.
- Convert UI page `1` to API `pageNumber=0`.
- Preserve filters and sort while changing pages.
- Treat manager objects as nullable.
- Treat create and update responses as a different shape from list/get responses.
- Handle validation errors using the `properties` object in the 400 response.
- Handle not-found responses using the `message` field.
