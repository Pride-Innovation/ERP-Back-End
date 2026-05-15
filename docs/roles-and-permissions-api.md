# Roles and Permissions API Documentation

## Overview

This document covers all role and permission-related requests for the frontend team.

Available role endpoints:

- `GET /api/v1/roles` for paginated listing with filters
- `GET /api/v1/roles/{roleID}` for single role details
- `POST /api/v1/roles` for creating a role
- `PUT /api/v1/roles/{roleID}` for updating a role
- `PUT /api/v1/roles/{roleID}/add-permissions/{permID}` for assigning a permission to a role
- `PUT /api/v1/roles/{roleID}/remove-permissions/{permID}` for removing a permission from a role
- `DELETE /api/v1/roles/{roleID}` for deleting a role

Available permission endpoints:

- `GET /api/v1/permissions` for listing all permissions
- `POST /api/v1/permissions` for creating a permission
- `PUT /api/v1/permissions/{permID}` for updating a permission
- `DELETE /api/v1/permissions/{permID}` for deleting a permission

All endpoints require:

- `Authorization: Bearer <jwt-token>`

---

# Role Endpoints

## Role List Endpoint

- Method: `GET`
- URL: `/api/v1/roles`
- Response type: Spring `Page<Role>`

### Query Parameters

All query parameters are optional.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `pageNumber` | number | `0` | Zero-based page index. First page is `0`. |
| `pageSize` | number | `10` | Number of records returned per page. |
| `sortBy` | string | `id` | Field used for sorting. Recommended values: `id`, `name`. |
| `sortDirection` | string | `ASC` | Sort direction. Allowed values: `ASC`, `DESC`. |
| `name` | string | none | Case-insensitive partial match on role name. |

### Filter Behavior

- The `name` filter uses a case-insensitive `contains` match.
- If no filters are supplied, the API returns all roles with pagination.

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
- Use valid role entity fields only for `sortBy`.

### Example Requests

#### First page with defaults

```http
GET /api/v1/roles
Authorization: Bearer <jwt-token>
```

#### Second page with page size 5

```http
GET /api/v1/roles?pageNumber=1&pageSize=5
Authorization: Bearer <jwt-token>
```

#### Search by role name

```http
GET /api/v1/roles?name=admin
Authorization: Bearer <jwt-token>
```

#### Combined filter with sorting and pagination

```http
GET /api/v1/roles?pageNumber=0&pageSize=10&sortBy=name&sortDirection=ASC&name=manager
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "content": [
    {
      "id": 1,
      "name": "ADMIN",
      "description": "Administrator role with full system access",
      "createDate": "2024-01-15T10:30:00",
      "lastModified": "2024-01-15T10:30:00",
      "createdBy": 1,
      "lastModifiedBy": 1,
      "permissions": [
        {
          "id": 1,
          "name": "READ_USER"
        },
        {
          "id": 2,
          "name": "CREATE_USER"
        }
      ]
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
  "totalPages": 1,
  "totalElements": 3,
  "last": true,
  "size": 10,
  "number": 0,
  "sort": {
    "empty": false,
    "sorted": true,
    "unsorted": false
  },
  "first": true,
  "numberOfElements": 3,
  "empty": false
}
```

## Single Role Endpoint

- Method: `GET`
- URL: `/api/v1/roles/{roleID}`
- Response type: `Role`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `roleID` | number | Role ID |

### Example Request

```http
GET /api/v1/roles/1
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "id": 1,
  "name": "ADMIN",
  "description": "Administrator role with full system access",
  "createDate": "2024-01-15T10:30:00",
  "lastModified": "2024-01-15T10:30:00",
  "createdBy": 1,
  "lastModifiedBy": 1,
  "permissions": [
    {
      "id": 1,
      "name": "READ_USER"
    },
    {
      "id": 2,
      "name": "CREATE_USER"
    }
  ]
}
```

## Create Role Endpoint

- Method: `POST`
- URL: `/api/v1/roles`
- Request body type: `RoleRequestDTO`
- Response type: `Role`

### Request Body

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | Yes | Role name. Must be unique. |
| `description` | string | No | Brief description of the role's purpose. |

### Validation Rules

- `name` is required and cannot be blank. Must be unique across all roles.
- `description` is optional.
- Permissions are not assigned at creation time. Use the assign-permission endpoint after creating the role.

### Example Request

```http
POST /api/v1/roles
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "BRANCH_MANAGER",
  "description": "Role for branch managers with branch-level access"
}
```

### Example Success Response

`201 Created`

```json
{
  "id": 4,
  "name": "BRANCH_MANAGER",
  "description": "Role for branch managers with branch-level access",
  "createDate": "2024-05-15T09:00:00",
  "lastModified": "2024-05-15T09:00:00",
  "createdBy": 1,
  "lastModifiedBy": 1,
  "permissions": []
}
```

## Update Role Endpoint

- Method: `PUT`
- URL: `/api/v1/roles/{roleID}`
- Request body type: `RoleRequestDTO`
- Response type: `Role`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `roleID` | number | Role ID |

### Request Body

Use the same request body shape as create.

```json
{
  "name": "BRANCH_MANAGER",
  "description": "Updated description for branch manager role"
}
```

### Update Notes

- This is a full update request, not a partial patch.
- The frontend should send the full role payload.
- `name` must still be present and unique during update.
- Updating a role does not affect its assigned permissions.

### Example Request

```http
PUT /api/v1/roles/4
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "BRANCH_MANAGER",
  "description": "Updated description for branch manager role"
}
```

### Example Success Response

`200 OK`

```json
{
  "id": 4,
  "name": "BRANCH_MANAGER",
  "description": "Updated description for branch manager role",
  "createDate": "2024-05-15T09:00:00",
  "lastModified": "2024-05-15T09:15:00",
  "createdBy": 1,
  "lastModifiedBy": 1,
  "permissions": [
    {
      "id": 3,
      "name": "READ_ROLE"
    }
  ]
}
```

## Assign Permission to Role Endpoint

- Method: `PUT`
- URL: `/api/v1/roles/{roleID}/add-permissions/{permID}`
- Response type: `Role`

### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `roleID` | number | ID of the role |
| `permID` | number | ID of the permission to assign |

### Behavior Notes

- If the permission is already assigned to the role, the operation is idempotent and returns the role unchanged.
- Both the role and the permission must exist.

### Example Request

```http
PUT /api/v1/roles/4/add-permissions/5
Authorization: Bearer <jwt-token>
```

### Example Success Response

`200 OK`

```json
{
  "id": 4,
  "name": "BRANCH_MANAGER",
  "description": "Updated description for branch manager role",
  "createDate": "2024-05-15T09:00:00",
  "lastModified": "2024-05-15T09:20:00",
  "createdBy": 1,
  "lastModifiedBy": 1,
  "permissions": [
    {
      "id": 3,
      "name": "READ_ROLE"
    },
    {
      "id": 5,
      "name": "READ_USER"
    }
  ]
}
```

## Remove Permission from Role Endpoint

- Method: `PUT`
- URL: `/api/v1/roles/{roleID}/remove-permissions/{permID}`
- Response type: `Role`

### Path Parameters

| Parameter | Type | Description |
|---|---|---|
| `roleID` | number | ID of the role |
| `permID` | number | ID of the permission to remove |

### Behavior Notes

- If the permission is not currently assigned to the role, the operation is idempotent and returns the role unchanged.
- Both the role and the permission must exist.

### Example Request

```http
PUT /api/v1/roles/4/remove-permissions/5
Authorization: Bearer <jwt-token>
```

### Example Success Response

`200 OK`

```json
{
  "id": 4,
  "name": "BRANCH_MANAGER",
  "description": "Updated description for branch manager role",
  "createDate": "2024-05-15T09:00:00",
  "lastModified": "2024-05-15T09:25:00",
  "createdBy": 1,
  "lastModifiedBy": 1,
  "permissions": [
    {
      "id": 3,
      "name": "READ_ROLE"
    }
  ]
}
```

## Delete Role Endpoint

- Method: `DELETE`
- URL: `/api/v1/roles/{roleID}`
- Response type: `DeleteResponse`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `roleID` | number | Role ID |

### Example Request

```http
DELETE /api/v1/roles/4
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

---

# Permission Endpoints

## Permission List Endpoint

- Method: `GET`
- URL: `/api/v1/permissions`
- Response type: `List<Permission>`

### Notes

- Permissions are not paginated. The full list is always returned.
- Use this endpoint to populate permission selection dropdowns when managing roles.

### Example Request

```http
GET /api/v1/permissions
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
[
  {
    "id": 1,
    "name": "READ_USER"
  },
  {
    "id": 2,
    "name": "CREATE_USER"
  },
  {
    "id": 3,
    "name": "UPDATE_USER"
  },
  {
    "id": 4,
    "name": "DELETE_USER"
  },
  {
    "id": 5,
    "name": "READ_ROLE"
  }
]
```

## Create Permission Endpoint

- Method: `POST`
- URL: `/api/v1/permissions`
- Request body type: `PermissionRequestDTO`
- Response type: `Permission`

### Request Body

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | Yes | Permission name. Must be unique. |

### Validation Rules

- `name` is required and cannot be blank. Must be unique across all permissions.

### Example Request

```http
POST /api/v1/permissions
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "CREATE_BRANCH"
}
```

### Example Success Response

`201 Created`

```json
{
  "id": 6,
  "name": "CREATE_BRANCH"
}
```

## Update Permission Endpoint

- Method: `PUT`
- URL: `/api/v1/permissions/{permID}`
- Request body type: `PermissionRequestDTO`
- Response type: `Permission`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `permID` | number | Permission ID |

### Request Body

```json
{
  "name": "CREATE_BRANCH"
}
```

### Update Notes

- Only the permission name can be updated.
- The new name must be unique.
- All roles that had this permission assigned retain it under the new name.

### Example Request

```http
PUT /api/v1/permissions/6
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "MANAGE_BRANCH"
}
```

### Example Success Response

`200 OK`

```json
{
  "id": 6,
  "name": "MANAGE_BRANCH"
}
```

## Delete Permission Endpoint

- Method: `DELETE`
- URL: `/api/v1/permissions/{permID}`
- Response type: `DeleteResponse`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `permID` | number | Permission ID |

### Behavior Notes

- Deleting a permission automatically removes it from all roles it was assigned to.
- This operation cannot be undone.

### Example Request

```http
DELETE /api/v1/permissions/6
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

---

## Shared Response Shapes

### Role object shape

| Field | Type | Notes |
|---|---|---|
| `id` | number | Role ID |
| `name` | string | Unique role name |
| `description` | string or `null` | Description of the role |
| `createDate` | string (ISO datetime) | When the role was created |
| `lastModified` | string (ISO datetime) | When the role was last updated |
| `createdBy` | number | User ID of the creator |
| `lastModifiedBy` | number | User ID of the last modifier |
| `permissions` | array | List of permission objects assigned to this role |

### Permission object shape (embedded in Role)

| Field | Type |
|---|---|
| `id` | number |
| `name` | string |

### Permission object shape (standalone)

| Field | Type |
|---|---|
| `id` | number |
| `name` | string |

### DeleteResponse shape

| Field | Type |
|---|---|
| `message` | string |
| `success` | boolean |

---

## Error Handling

Typical non-success responses:

- `400 Bad Request`: validation failure or duplicate name
- `401 Unauthorized`: missing or invalid token
- `403 Forbidden`: authenticated user lacks permission
- `404 Not Found`: referenced role or permission does not exist
- `409 Conflict`: data integrity violation
- `500 Internal Server Error`: unexpected backend failure

### Example Validation Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation error",
  "instance": "/api/v1/roles",
  "properties": {
    "name": "Role name is required"
  }
}
```

### Example Duplicate Name Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "A role with the name 'ADMIN' already exists",
  "instance": "/api/v1/roles"
}
```

### Example Not Found Response

```json
{
  "message": "Role with ID 4 is not found"
}
```

---

## Recommended Frontend Integration

### For role management pages

- Use the list endpoint for the roles table.
- Keep filter, sort, and pagination state in the URL.
- Reset `pageNumber` to `0` whenever the name filter changes.
- Read roles from `content`, `totalElements`, and `totalPages`.

### For permission assignment UI

- Fetch the full permission list once on page load using `GET /api/v1/permissions`.
- Show the current role permissions and the full list side by side.
- Call add-permissions or remove-permissions per individual permission change.
- Refetch the role after each assignment or removal to reflect the updated permissions list.

### For delete flows

- Show a confirmation dialog before deleting a role or permission.
- Note that deleting a permission removes it from all roles automatically.
- On success, remove the row locally or refetch the current page.

## Frontend Implementation Checklist

- Send only non-empty filters to the roles list endpoint.
- Convert UI page `1` to API `pageNumber=0`.
- Preserve filters and sort while changing pages.
- Treat `description` as nullable in role responses.
- Load the full permissions list once; avoid re-fetching on every role detail view.
- Handle validation errors using the `properties` object in the 400 response.
- Handle not-found errors using the `message` field.
- Use separate requests for assigning and removing permissions; there is no bulk assign endpoint.
