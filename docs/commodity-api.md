# Commodity API Documentation

## Overview

This document covers all commodity-related requests for the frontend team.

Available commodity endpoints:

- `GET /api/v1/commodities` for paginated listing with filters
- `GET /api/v1/commodities/{id}` for single commodity details
- `POST /api/v1/commodities` for creating a commodity
- `PUT /api/v1/commodities/{id}` for updating a commodity
- `DELETE /api/v1/commodities/{id}` for deleting a commodity

All commodity endpoints require:

- `Authorization: Bearer <jwt-token>`

---

## Commodity List Endpoint

- Method: `GET`
- URL: `/api/v1/commodities`
- Response type: Spring `Page<Commodity>`

### Query Parameters

All query parameters are optional.

| Parameter | Type | Default | Description |
|---|---|---|---|
| `pageNumber` | number | `0` | Zero-based page index. First page is `0`. |
| `pageSize` | number | `10` | Number of records returned per page. |
| `sortBy` | string | `id` | Field used for sorting. Recommended values: `id`, `name`, `groupName`. |
| `sortDirection` | string | `ASC` | Sort direction. Allowed values: `ASC`, `DESC`. |
| `name` | string | none | Case-insensitive partial match on commodity name. |
| `groupName` | string | none | Case-insensitive partial match on group name (e.g. `Pieces`, `Dozens`). |
| `assetTypeId` | number | none | Exact match on asset type ID. |

### Filter Behavior

- Filters can be combined in the same request.
- All supplied filters are combined with `AND`.
- `name` and `groupName` use a case-insensitive `contains` match.
- `assetTypeId` uses exact equality.
- If no filters are supplied, the API returns all commodities with pagination.

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
- Use valid commodity entity fields only for `sortBy`.

### Example Requests

#### First page with defaults

```http
GET /api/v1/commodities
Authorization: Bearer <jwt-token>
```

#### Second page with page size 20

```http
GET /api/v1/commodities?pageNumber=1&pageSize=20
Authorization: Bearer <jwt-token>
```

#### Search by commodity name

```http
GET /api/v1/commodities?name=chair
Authorization: Bearer <jwt-token>
```

#### Filter by group name

```http
GET /api/v1/commodities?groupName=pieces
Authorization: Bearer <jwt-token>
```

#### Filter by asset type

```http
GET /api/v1/commodities?assetTypeId=2
Authorization: Bearer <jwt-token>
```

#### Combined filters with sorting and pagination

```http
GET /api/v1/commodities?pageNumber=0&pageSize=10&sortBy=name&sortDirection=ASC&name=chair&assetTypeId=2
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "content": [
    {
      "id": 1,
      "name": "Office Chair",
      "groupName": "Pieces",
      "assetType": {
        "id": 2,
        "name": "Furniture"
      },
      "suppliers": [
        {
          "id": 3,
          "name": "Techno Supplies Ltd",
          "address": "Plot 14, Kampala Road, Kampala",
          "telephone": "+256700123456",
          "email": "techno@pridebank.co.ug"
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
  "totalPages": 4,
  "totalElements": 35,
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

## Single Commodity Endpoint

- Method: `GET`
- URL: `/api/v1/commodities/{id}`
- Response type: `Commodity`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Commodity ID |

### Example Request

```http
GET /api/v1/commodities/1
Authorization: Bearer <jwt-token>
```

### Example Success Response

```json
{
  "id": 1,
  "name": "Office Chair",
  "groupName": "Pieces",
  "assetType": {
    "id": 2,
    "name": "Furniture"
  },
  "suppliers": [
    {
      "id": 3,
      "name": "Techno Supplies Ltd",
      "address": "Plot 14, Kampala Road, Kampala",
      "telephone": "+256700123456",
      "email": "techno@pridebank.co.ug"
    }
  ]
}
```

---

## Create Commodity Endpoint

- Method: `POST`
- URL: `/api/v1/commodities`
- Request body type: `CommodityDTO`
- Response type: `Commodity`

### Request Body

| Field | Type | Required | Notes |
|---|---|---|---|
| `name` | string | Yes | Commodity name. |
| `groupName` | string | Yes | Unit grouping for this commodity (e.g. `Pieces`, `Dozens`, `Litres`). |
| `assetType` | number | Yes | ID of the asset type this commodity belongs to. |

### Validation Rules

- `name` is required and cannot be blank.
- `groupName` is required and cannot be blank.
- `assetType` is required and must reference an existing asset type.
- The combination of `name`, `groupName`, and `assetType` must be unique. A duplicate combination is rejected with `400`.

### Example Request

```http
POST /api/v1/commodities
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Office Chair",
  "groupName": "Pieces",
  "assetType": 2
}
```

### Example Success Response

HTTP `201 Created`

```json
{
  "id": 1,
  "name": "Office Chair",
  "groupName": "Pieces",
  "assetType": {
    "id": 2,
    "name": "Furniture"
  },
  "suppliers": []
}
```

---

## Update Commodity Endpoint

- Method: `PUT`
- URL: `/api/v1/commodities/{id}`
- Request body type: `CommodityDTO`
- Response type: `Commodity`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Commodity ID |

### Request Body

Use the same request body shape as create.

```json
{
  "name": "Office Chair",
  "groupName": "Pieces",
  "assetType": 2
}
```

### Update Notes

- This is a full update, not a partial patch.
- The frontend should send the complete commodity payload.
- Required fields must still be present during update.
- Supplier links are managed through the Supplier API, not here.

### Example Request

```http
PUT /api/v1/commodities/1
Authorization: Bearer <jwt-token>
Content-Type: application/json

{
  "name": "Office Chair Updated",
  "groupName": "Pieces",
  "assetType": 2
}
```

### Example Success Response

HTTP `200 OK`

```json
{
  "id": 1,
  "name": "Office Chair Updated",
  "groupName": "Pieces",
  "assetType": {
    "id": 2,
    "name": "Furniture"
  },
  "suppliers": [
    {
      "id": 3,
      "name": "Techno Supplies Ltd",
      "address": "Plot 14, Kampala Road, Kampala",
      "telephone": "+256700123456",
      "email": "techno@pridebank.co.ug"
    }
  ]
}
```

---

## Delete Commodity Endpoint

- Method: `DELETE`
- URL: `/api/v1/commodities/{id}`
- Response type: `DeleteResponse`

### Path Parameter

| Parameter | Type | Description |
|---|---|---|
| `id` | number | Commodity ID |

### Example Request

```http
DELETE /api/v1/commodities/1
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

### Commodity object shape

| Field | Type | Notes |
|---|---|---|
| `id` | number | Commodity ID |
| `name` | string | Commodity name |
| `groupName` | string | Unit grouping (e.g. `Pieces`, `Dozens`) |
| `assetType` | object | Asset type details. Never `null` since it is required on create. |
| `suppliers` | array | List of suppliers linked to this commodity. Empty array if none linked. |

### AssetType object shape

| Field | Type |
|---|---|
| `id` | number |
| `name` | string |

### Supplier object shape (inside commodity response)

| Field | Type | Notes |
|---|---|---|
| `id` | number | Supplier ID |
| `name` | string | Supplier name |
| `address` | string or `null` | Supplier address |
| `telephone` | string | Supplier telephone |
| `email` | string | Supplier email |

### DeleteResponse shape

| Field | Type |
|---|---|
| `message` | string |
| `success` | boolean |

---

## Error Handling

Typical non-success responses:

- `400 Bad Request`: validation failure or duplicate commodity
- `401 Unauthorized`: missing or invalid token
- `403 Forbidden`: authenticated user lacks permission
- `404 Not Found`: referenced commodity or asset type does not exist
- `409 Conflict`: data integrity violation
- `500 Internal Server Error`: unexpected backend failure

### Example Validation Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation error",
  "instance": "/api/v1/commodities",
  "properties": {
    "name": "Commodity name is required",
    "groupName": "Group name is required",
    "assetType": "Asset type is required"
  }
}
```

### Example Duplicate Commodity Error Response

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "A commodity with name 'Office Chair', group 'Pieces' and the same asset type already exists",
  "instance": "/api/v1/commodities"
}
```

### Example Not Found Response

```json
{
  "message": "Commodity with ID 1 not found"
}
```

### Example Asset Type Not Found Response

```json
{
  "message": "Asset type with ID 99 not found"
}
```

---

## Recommended Frontend Integration

### For listing pages

- Use the list endpoint for the table/grid.
- Keep filters, sorting, and pagination state in the URL if possible.
- Reset `pageNumber` to `0` whenever filters change.
- Read commodity rows from `content`.
- Read `totalElements` and `totalPages` for pagination controls.
- Use `assetTypeId` to filter commodities scoped to a specific asset type.

### For create and update forms

- Use a dropdown for `assetType`, populated from the asset types list endpoint.
- Store the selected asset type ID in the `assetType` field of the request body.
- Send the complete payload on update.
- Supplier associations are managed through the Supplier API; there is no supplier field on the commodity create/update form.
- After create or update, either refetch the commodity list or call the single-commodity endpoint.

### For delete flows

- Show a confirmation dialog before delete.
- On success, remove the row locally or refetch the current page.

---

## Frontend Implementation Checklist

- Send only non-empty filters to the list endpoint.
- Convert UI page `1` to API `pageNumber=0`.
- Preserve filters and sort while changing pages.
- Treat `suppliers` as an array that may be empty.
- Do not include supplier fields in create or update requests — supplier links are managed via the Supplier API.
- Handle validation errors using the `properties` object in the `400` response.
- Handle not-found responses using the `message` field.
- Handle duplicate commodity errors using the `detail` field in the `400` response.
