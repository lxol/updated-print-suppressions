
# updated-print-suppressions

[![Build Status](https://travis-ci.org/hmrc/updated-print-suppressions.svg?branch=master)](https://travis-ci.org/hmrc/updated-print-suppressions) [ ![Download](https://api.bintray.com/packages/hmrc/releases/updated-print-suppressions/images/download.svg) ](https://bintray.com/hmrc/releases/updated-print-suppressions/_latestVersion)

Microservice responsible for the print suppression preference of a customer.

# API

| Path                                                                                     | Supported Methods | Description                                                                                                                                                                                   |
| -----------------------------------------------------------------------------------------| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/preferences/sa/individual/print-suppression`                                           | GET               | The tax identifiers of all customers whose print may be suppressed, together with the form identifiers which may be suppressed for them [More...](#get-preferencessaindividualprint-suppression)                 |

### GET /preferences/sa/individual/print-suppression

The tax identifiers of all customers whose print may be suppressed, together with the form identifiers which may be suppressed for them. Accepts parameters:

| Name         | Description |
| ------------ | ----------- |
| `updated-on` | Mandatory field. Limits the response to preferences changed on a given date. Must be a date in the past formatted as `yyyy-mm-dd`. |
| `limit`      | Limits number of preferences per response. Must be an integer between 0 and 20000. If not supplied, defaults to 20,000 |

Example URL: `/preferences/sa/individual/print-suppression?updated-on=2013-12-30&limit=100`

Responds with status:

* `200` with a response body if the request was successful
* `400` if the parameters provided are invalid

Example response:

```javascript
{
  "pages":3,
  "next":"/preferences/sa/individual/print-suppression?updated-on=2013-12-30&offset=1234&limit=100",
  "updates":[
    {"id":"12345678","idType":"utr","formIds":[]},
    {"id":"10000000","idType":"utr","formIds":[]},
    {"id":"10000001","idType":"utr","formIds":["ABC","123","XYZ"]},
    [...]
  ]
}
```

The `next` property gives the URL of the next page of preferences, if available. Each item in `updates` gives an identifier along with the form IDs that may be suppressed for that customer.

----------

# Admin API

| Path                                                                                     | Supported Methods | Description                                                                                                                                                                                   |
| -----------------------------------------------------------------------------------------| ----------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `/preferences/sa/individual/print-suppression`                                           | POST              | Insert or update a updated print preference record [More...](#post-preferencessaindividualprint-suppression)                 |

### POST /preferences/sa/individual/print-suppression

Insert or update a updated print preference record

| Name         | Description |
| ------------ | ----------- |
| `date` | Mandatory field. The date the preference has been updated. Must be a date formatted as `yyyy-mm-dd`. |


Example body:

```javascript
{"id":"10000001","idType":"utr","formIds":["ABC","123","XYZ"]},
```
Responds with status:

* `200` when the record is inserted or updated successfully
* `500` in any other case


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
