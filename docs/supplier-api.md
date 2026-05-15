# Supplier API Documentation

## Overview

This document covers all supplier-related requests for the frontend team.

Available supplier endpoints:

- `GET /api/v1/suppliers` for paginated listing with filters
- `GET /api/v1/suppliers/{id}` for single supplier details
- `POST /api/v1/suppliers` for creating a supplier
- `PUT /api/v1/suppliers/{id}` for updating a supplier
- `DELETE /api/v1/suppliers/{id}` for deleting a supplier

All supplier endpoints require:

- `Authorization: Bearer <jwt-token>`

---

## Supplier List Endpoint

- Method: `GET`
- URL: `/api/v1/suppliers`
- Response type: Spring `Page<Supplier>`

### Query Parameters

All query parameters are optional.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `pageNumber` | number | `0` | Zero-based page index. First page is `0`. |
| `pageSize` | number | `10` | Number of records returned per page. |
| `sortBy` | string | `id` | Field used for sorting. Recommended values: `id`, `name`, `email`, `telephone`, `address`. |
| `sortDirection` | string | `ASC` | Sort direction. Allowed values: `ASC`, `DESC`. |
| `name` | string | none | Case-insensitive partial match on supplier name. |
| `email` | string | none | Case-insensitive partial match on supplier email. |
| `address` | string | none | Case-insensitive partial match on supplier address. |
| `telephone` | string | none | Partial match on supplier telephone number. |

### Filter Behavior

- Filters can be combined in the same request.
- All supplied filters are combined with `AND`.
- `name`, `email`, and `address` use a case-insensitive `contains` match.
- `telephone` uses a plain `contains` match.
- If no filters are supplied, the API returns all suppliers with pagination.

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
- Use valid supplier entity fields only for `sortBy`.

### Example Requests

#### First page with defaults

```http
GET /api/v1/suppliers
Authorization: Bearer <jwt-token>
```

#### Second page with page size 20

```http
GET /api/v1/suppliers?pageNumber=1&pageSize=20
Authorization: Bearer <jwt-token>
```

#### Search by supplier name

```http
GET /api/v1/suppliers?name=techno
Authorization: Bearer <jwt-token>
```

#### Filter by email domain

```http
GET /api/v1/suppliers?email=pridebank.co.ug
Authorization: Bearer <jwt-token>
```

#### Filter by address

```http
GET /api/v1/suppliers?address=kampala
Authorization: Bearer <jwt-token>
```

#### Combined filters with sorting and pagination

```http
GET /api/v1/suppliers?pageNumber=0&pageSize=10&sortBy=name&sortDirection=ASC&name=techno&address=kampala
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "content": [
    {
      "id": 3,
      "name": "Techno Supplies Ltd",
      "address": "Plot 14, Kampala Road, Kampala",
      "telephone": "+256700123456",
      "email": "techno@pridebank.co.ug",
      "commodities": [
        {
          "id": 1,
          "name": "Office Chair",
          "groupName": "Pieces",
          "assetType": {
            "id": 2,
            "name": "Furniture"
          }
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
  "totalPages": 3,
  "totalElements": 22,
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

## Single Supplier Endpoint

- Method: `GET`
- URL: `/api/v1/suppliers/{id}`
- Response type: `Supplier`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Supplier ID |

### Example Request

```http
GET /api/v1/suppliers/3
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "id": 3,
  "name": "Techno Supplies Ltd",
  "address": "Plot 14, Kampala Road, Kampala",
  "telephone": "+256700123456",
  "email": "techno@pridebank.co.ug",
  "commodities": [
    {
      "id": 1,
      "name": "Office Chair",
      "groupName": "Pieces",
      "assetType": {
        "id": 2,
        "name": "Furniture"
      }
    },
    {
      "id": 4,
      "name": "Office Desk",
      "groupName": "Pieces",
      "assetType": {
        "id": 2,
        "name": "Furniture"
      }
    }
  ]
}
```

---

## Create Supplier Endpoint

- Method: `POST`
- URL: `/api/v1/suppliers`
- Request body type: `SupplierDTO`
- Response type: `Supplier`

### Request Body

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | Yes | Supplier name. Must be unique (case-insensitive). |
| `telephone` | string | Yes | Must be a valid Ugandan phone number (`+256` followed by 9 digits). |
| `email` | string | Yes | Must be a valid email address. |
| `address` | string | No | Physical or mailing address of the supplier. |
| `commodity` | number | No | Single commodity ID to link to the supplier. |
| `commodities` | array of numbers | No | Multiple commodity IDs to link to the supplier. |

### Commodity Assignment Notes

- Both `commodity` and `commodities` can be used together in the same request; the backend merges them.
- Use `commodity` for a single item assignment or `commodities` for bulk assignment.
- All supplied commodity IDs must reference existing commodities.

### Validation Rules

- `name` is required and cannot be blank. Must be unique across all suppliers.
- `telephone` is required and must match the Ugandan phone format: `+256` followed by exactly 9 digits (e.g. `+256700000000`).
- `email` is required and must be a valid email address.
- `address` is optional.
- Commodity IDs are optional, but if supplied they must reference existing commodities.

### Example Request

```http
POST /api/v1/suppliers
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Techno Supplies Ltd",
  "address": "Plot 14, Kampala Road, Kampala",
  "telephone": "+256700123456",
  "email": "techno@pridebank.co.ug",
  "commodities": [1, 4]
}
```

### Example Success Response

HTTP `201 Created`

```json
{
  "id": 3,
  "name": "Techno Supplies Ltd",
  "address": "Plot 14, Kampala Road, Kampala",
  "telephone": "+256700123456",
  "email": "techno@pridebank.co.ug",
  "commodities": [
    {
      "id": 1,
      "name": "Office Chair",
      "groupName": "Pieces",
      "assetType": {
        "id": 2,
        "name": "Furniture"
      }
    },
    {
      "id": 4,
      "name": "Office Desk",
      "groupName": "Pieces",
      "assetType": {
        "id": 2,
        "name": "Furniture"
      }
    }
  ]
}
```

---

## Update Supplier Endpoint

- Method: `PUT`
- URL: `/api/v1/suppliers/{id}`
- Request body type: `SupplierDTO`
- Response type: `Supplier`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Supplier ID |

### Request Body

Use the same request body shape as create.

```json
{
  "name": "Techno Supplies Ltd",
  "address": "Plot 14, Kampala Road, Kampala",
  "telephone": "+256700123456",
  "email": "techno@pridebank.co.ug",
  "commodities": [1, 4]
}
```

### Update Notes

- This is a full update, not a partial patch.
- The frontend should send the complete supplier payload.
- Required fields must still be present during update.
- The `commodities` list in the request becomes the new full set of linked commodities. Any commodity previously linked to the supplier that is not in the request will be unlinked.
- To remove all commodities from a supplier, send `"commodities": []` and omit `commodity`.

### Example Request

```http
PUT /api/v1/suppliers/3
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Techno Supplies Ltd Updated",
  "address": "Plot 22, Jinja Road, Kampala",
  "telephone": "+256700123456",
  "email": "techno.updated@pridebank.co.ug",
  "commodities": [1]
}
```

### Example Success Response

HTTP `200 OK`

```json
{
  "id": 3,
  "name": "Techno Supplies Ltd Updated",
  "address": "Plot 22, Jinja Road, Kampala",
  "telephone": "+256700123456",
  "email": "techno.updated@pridebank.co.ug",
  "commodities": [
    {
      "id": 1,
      "name": "Office Chair",
      "groupName": "Pieces",
      "assetType": {
        "id": 2,
        "name": "Furniture"
      }
    }
  ]
}
```

---

## Delete Supplier Endpoint

- Method: `DELETE`
- URL: `/api/v1/suppliers/{id}`
- Response type: `DeleteResponse`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Supplier ID |

### Example Request

```http
DELETE /api/v1/suppliers/3
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

### Supplier object shape

| Field | Type | Notes |
|---|---|---|
| `id` | number | Supplier ID |
| `name` | string | Supplier name |
| `address` | string or `null` | Physical address |
| `telephone` | string | Ugandan phone number |
| `email` | string | Supplier email address |
| `commodities` | array | List of linked commodity objects. Empty array if none linked. |

### Commodity object shape (inside supplier response)

| Field | Type | Notes |
|---|---|---|
| `id` | number | Commodity ID |
| `name` | string | Commodity name |
| `groupName` | string | Unit grouping (e.g. `Pieces`, `Dozens`) |
| `assetType` | object or `null` | Asset type details |

### AssetType object shape

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

- `400 Bad Request`: validation failure or duplicate supplier name
- `401 Unauthorized`: missing or invalid token
- `403 Forbidden`: authenticated user lacks permission
- `404 Not Found`: referenced supplier or commodity does not exist
- `409 Conflict`: data integrity violation
- `500 Internal Server Error`: unexpected backend failure

### Example Validation Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation error",
  "instance": "/api/v1/suppliers",
  "properties": {
    "name": "Supplier name is required",
    "telephone": "Invalid Ugandan phone number",
    "email": "Email address must be valid"
  }
}
```

### Example Duplicate Name Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "A supplier with the name 'Techno Supplies Ltd' already exists",
  "instance": "/api/v1/suppliers"
}
```

### Example Not Found Response

```json
{
  "message": "Supplier with ID 3 not found"
}
```

### Example Commodity Not Found Response

```json
{
  "message": "Commodity with ID 99 not found"
}
```

---

## Recommended Frontend Integration

### For listing pages

- Use the list endpoint for the table/grid.
- Keep filters, sorting, and pagination state in the URL if possible.
- Reset `pageNumber` to `0` whenever filters change.
- Read supplier rows from `content`.
- Read `totalElements` and `totalPages` for pagination controls.

### For create and update forms

- Use a multi-select or tag-input for the `commodities` field, storing selected commodity IDs.
- Alternatively use the single `commodity` field when assigning only one commodity.
- Send the complete payload on update, including the full current list of `commodities`.
- To clear all commodities, send `"commodities": []` and do not include `commodity`.
- After create or update, either refetch the supplier list or call the single-supplier endpoint.

### For delete flows

- Show a confirmation dialog before delete.
- On success, remove the row locally or refetch the current page.

---

## Frontend Implementation Checklist

- Send only non-empty filters to the list endpoint.
- Convert UI page `1` to API `pageNumber=0`.
- Preserve filters and sort while changing pages.
- Treat `address` as nullable in display.
- Treat `commodities` as an array that may be empty.
- On update, always send the full `commodities` list; omitting it will unlink all commodities.
- Use the `+256XXXXXXXXX` format for all telephone inputs (13 characters total).
- Handle validation errors using the `properties` object in the `400` response.
- Handle not-found responses using the `message` field.
- Handle duplicate name errors using the `detail` field in the `400` response.
