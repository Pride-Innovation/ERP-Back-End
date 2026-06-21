Asset Management, Inventory Management, Store Management and Movement Workflow Requirements
1. Introduction
The Asset Management System shall provide a centralized platform for managing organizational assets, inventory, requests, approvals, store operations, stock movements, repairs, temporary allocations, and disposals across Head Office and Branch locations.
The system shall maintain complete audit trails and accountability for every asset and inventory item from procurement through assignment, maintenance, transfer, and eventual disposal.
 
2. Core Modules
The solution shall consist of the following core modules:
1.	Asset Management
2.	Inventory Management
3.	Request Management
4.	Approval Workflow Management
5.	Store Management
6.	Movement Management
7.	Asset Assignment Management
8.	Repair and Maintenance Management
9.	Disposal Management
10.	Reporting and Audit Management
These modules shall work together to provide complete visibility and control over organizational assets and inventory.
 
3. Organizational Structure
The system shall support:
Locations
•	Head Office
•	Branches
Departments (In Head Office)
Examples include:
•	Finance
•	Human Resource
•	Operations
•	Infrastructure
•	Compliance
•	Risk
•	Administration
Each user shall belong to a specific location and department.
 
4. Store Types
The system shall support three primary store types:
4.1 Admin Store
The Admin Store is the main inventory holding store.
Responsibilities:
•	Receive newly procured inventory.
•	Store available inventory.
•	Issue inventory to users.
•	Transfer inventory to other locations.
•	Transfer inventory to departments.
•	Monitor stock balances.
Each location shall have its own Admin Store.
Examples:
•	Head Office Admin Store
•	Gulu Branch Admin Store
•	Mbarara Branch Admin Store
 
4.2 IT Store
The IT Store shall be responsible for:
•	Defective assets received for repair.
•	Temporary replacement assets.
•	Repaired assets awaiting return.
•	Technical asset management.
The IT Store shall primarily operate from Head Office.
Examples:
•	Laptops
•	Desktops
•	Monitors
•	Printers
•	Network Equipment
 
4.3 Disposal Store
The Disposal Store shall hold:
•	Obsolete assets
•	Irreparable assets
•	Written-off assets
•	Assets approved for disposal
Assets moved into this store shall no longer be available for allocation or operational use.
 
5. Inventory Procurement and Stocking
Whenever inventory is procured:
1.	Inventory records shall be created.
2.	Items shall be assigned to a receiving location.
3.	Stock quantities shall automatically increase in the Admin Store of the receiving location.
In most cases:
•	Head Office procures inventory.
•	Inventory is initially stocked into the Head Office Admin Store.
•	Inventory may later be distributed to branches.
Examples:
•	Laptops
•	Computers
•	Furniture
•	Stationery
•	Printers
•	Office Equipment
•	Consumables
 
6. Request Fulfillment
After “Issuance approval” in the request flow, the system shall determine whether fulfillment requires:
A. Direct Issuance
or
B. Store Movement
The decision depends on the source and destination locations.
 
7. Asset and Inventory Issuance
The system shall support direct issuance of items from stores.
Direct Issuance Process
1.	Request approved.
2.	Store Administrator verifies stock availability.
3.	Item issued to requester.
4.	Requester acknowledges receipt.
5.	Inventory quantity is reduced.
 
Inventory Impact
Consumables
Examples:
•	Pens
•	Paper
•	Toner
•	Stationery
Result:
•	Store quantity decreases.
•	Issuance history maintained.
Assets
Examples:
•	Laptops
•	Computers
•	Printers
Result:
•	Store quantity decreases.
•	Asset assignment created.
•	Assignment history maintained.
 
8. Asset Assignment
When an asset is issued:
The system shall maintain:
•	Assigned User
•	Assignment Date
•	Asset Status
•	Department
•	Location
An asset may only be assigned to one active user at a time.
The system shall maintain assignment history.
 
9. Movement Management
The Movement Module shall manage transfers of inventory and assets.
The system shall support two movement categories:
A. Inter-Location Movements
Movement between different physical locations.
Examples:
•	Head Office → Branch
•	Branch → Head Office
•	Branch → Branch
Characteristics
•	Courier required
•	Dispatch required
•	Receiving process required
•	Movement tracking required
•	BOM/Admin involvement required
Inventory Impact
Source Store:
•	Quantity decreases
Destination Store:
•	Quantity increases
 
B. Intra-Location Transfers
Movement within the same physical location.
Examples:
•	Head Office Admin Store → Finance Department
•	Head Office Admin Store → User
•	IT Store → Infrastructure Department
•	Branch Admin Store → Branch User
Characteristics
•	No courier required
•	No shipment required
•	No transportation workflow required
•	Immediate transfer
•	Receipt acknowledgement required
Inventory Impact
Source Store:
•	Quantity decreases
Destination:
•	Quantity increases or asset assigned
 
9. Courier Management Rules
Courier information shall only be required when movement occurs between different locations.
Examples:
Required:
•	Head Office → Gulu Branch
•	Branch → Head Office
•	Branch → Branch
Not Required:
•	Head Office → Finance Department
•	Head Office → User
•	Branch Store → Branch User
•	IT Store → Infrastructure Department
 
10. Branch Operational Manager (BOM) Participation Rules
Branch Operational Managers and Store Administrators shall only participate when assets or inventory move between different locations.
BOM Required
Examples:
•	Head Office → Branch
•	Branch → Head Office
•	Branch → Branch
Responsibilities:
•	Verify dispatch.
•	Verify receipt.
•	Confirm accountability.
 
BOM Not Required
Examples:
•	Head Office Admin Store → User
•	Head Office Admin Store → Finance Department
•	Branch Admin Store → Branch User
The item shall be issued directly and acknowledged by the recipient.
 
11. Centralized Inventory Visibility
Head Office Administrators shall have visibility into inventory across all locations.
Capabilities include:
•	View stock balances.
•	View stock movements.
•	Monitor low stock levels.
•	Monitor consumption trends.
•	Initiate replenishment.
•	Transfer inventory.
 
12. Department-Level Inventory Visibility
Head Office Administrators shall also monitor inventory allocations within departments.
Examples:
•	Finance
•	Human Resource
•	Operations
•	Infrastructure
•	Risk
Administrators shall:
•	View allocations.
•	Transfer inventory between departments.
•	Reallocate stock when necessary.
No courier shall be required for departmental transfers.
 
13. Branch Replenishment Workflow
A branch may require additional inventory.
Process:
1.	Request submitted.
2.	Request approved.
3.	Head Office Admin reviews request.
4.	Movement created.
5.	Courier assigned.
6.	Items dispatched.
7.	Destination BOM receives items.
8.	Stock added to destination Admin Store.
9.	Item issued to requester.
10.	Requester acknowledges receipt.
 
14. Asset Repair Workflow
Users shall be able to report faulty assets.
Examples:
•	Laptop malfunction
•	Printer failure
•	Hardware damage
 
Repair Request Approval Process
The repair request shall pass through:
1.	Supervisor
2.	Manager
3.	Branch Operational Manager (BOM)
After approval:
A movement is initiated to Head Office.
 
Transfer to Head Office
The system shall capture:
•	Courier Details
•	Tracking Number
•	Dispatch Date
•	Supporting Documents
The asset is transferred from the Branch Admin Store to Head Office.
Inventory Impact:
Branch Store:
•	Quantity decreases
Head Office IT Store:
•	Quantity increases
 
15. Temporary Replacement Assets
While repairs are ongoing:
Infrastructure personnel may issue a temporary replacement asset.
Process:
1.	Temporary asset selected.
2.	Asset issued from IT Store.
3.	Asset assigned to user.
4.	Assignment marked as temporary.
Inventory Impact:
IT Store:
•	Quantity decreases
User:
•	Temporary assignment created
 
16. Asset Return After Repair
Once repair is completed:
1.	Repair completed by Infrastructure Team.
2.	Return movement initiated.
3.	Asset dispatched to originating location.
4.	Receiving officer confirms receipt.
5.	Original asset reassigned to user.
6.	Temporary asset returned to IT Store.
Inventory Impact:
Returned Asset:
•	Returned to operational use.
Temporary Asset:
•	Quantity restored to IT Store.
 
17. Disposal Workflow
If an asset cannot be repaired:
1.	Infrastructure recommends disposal.
2.	Disposal approvals obtained.
3.	Movement to Disposal Store initiated.
4.	Asset status changed to "Pending Disposal".
Inventory Impact:
IT Store:
•	Quantity decreases
Disposal Store:
•	Quantity increases
 
18. Movement Record Requirements
Every movement shall capture:
Source Information
•	Source Location
•	Source Department
•	Source Store
Destination Information
•	Destination Location
•	Destination Department
•	Destination Store
Item Information
•	Asset or Inventory Item
•	Quantity
•	Serial Number
•	Asset Tag
Logistics Information
Where applicable:
•	Courier Service
•	Tracking Number
•	Dispatch Date
•	Expected Delivery Date
•	Delivery Documents
Receiving Information
•	Receiving Officer
•	Receipt Date
•	Receipt Status
•	Remarks
Audit Information
•	Movement Initiator
•	Approvers
•	Approval Dates
•	Movement Status
•	Completion Date
 
22. Inventory Balancing Rules
The system shall automatically maintain inventory balances.
Store-to-Store Transfer
Source Store:
•	Quantity decreases
Destination Store:
•	Quantity increases
Store-to-User Issuance
Store:
•	Quantity decreases
User:
•	Assignment created
User Return
User:
•	Assignment removed
Store:
•	Quantity increases
Repair Transfer
Branch Store:
•	Quantity decreases
IT Store:
•	Quantity increases
Disposal Transfer
IT Store:
•	Quantity decreases
Disposal Store:
•	Quantity increases
 
23. Audit and Compliance
The system shall maintain complete audit trails for:
•	Requests
•	Approvals
•	Issuances
•	Assignments
•	Transfers
•	Repairs
•	Replacements
•	Disposals
No transaction shall be physically deleted from the system.
All historical records shall remain available for reporting and compliance purposes.
 
24. Expected Outcomes
The solution shall provide:
•	End-to-end asset lifecycle management.
•	Complete inventory visibility across all branches.
•	Controlled asset allocation.
•	Efficient repair management.
•	Centralized stock monitoring.
•	Automated movement tracking.
•	Accurate inventory balances.
•	Strong accountability and auditability.
•	Streamlined branch replenishment processes.
•	Comprehensive reporting and management oversight.

