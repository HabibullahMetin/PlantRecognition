from selenium import webdriver
from selenium.webdriver.common.keys import Keys
import json
import os
import urllib.request
import argparse

searchterm = 'bananas' # will also be the name of the folder
url = "https://www.google.co.in/search?q="+searchterm+"&source=lnms&tbm=isch"
# NEED TO DOWNLOAD CHROMEDRIVER, insert path to chromedriver inside parentheses in following line
browser = webdriver.Chrome("C:\webdrivers\chromedriver.exe")
browser.get(url)
header={'User-Agent':"Chrome/80.0.3987.106"}
counter = 0
succounter = 0

if not os.path.exists(searchterm):
    os.mkdir(searchterm)

for _ in range(500):
    browser.execute_script("window.scrollBy(0,10000)")

for x in browser.find_elements_by_xpath('//div[contains(@class,"rg_meta")]'):
    counter = counter + 1
    print("Total Count:", counter)
    print("Succsessful Count:", succounter)
    print("URL:",json.loads(x.get_attribute('innerHTML'))["ou"])

    img = json.loads(x.get_attribute('innerHTML'))["ou"]
    imgtype = json.loads(x.get_attribute('innerHTML'))["ity"]
  
    try:
        req = urllib.request.Request(img, headers={'User-Agent': header})
        raw_img = urllib.request.urlopen(req).read()
        File = open(os.path.join(searchterm , searchterm + "_" + str(counter) + "." + imgtype), "wb")
        File.write(raw_img)
        File.close()
        succounter = succounter + 1
    except:
            print("can't get img")

print(succounter, "pictures succesfully downloaded")
browser.close()