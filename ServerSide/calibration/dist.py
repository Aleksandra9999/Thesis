from __future__ import print_function
import numpy as np
import cv2
import glob
from matplotlib import pyplot as plt
from common import splitfn
import os

img_names_undistort = [img for img in glob.glob("./color/*.png")]
new_path = './undistord/'

camera_matrix = np.array([[1.26125746e+03, 0.00000000e+00, 9.40592038e+02],
                          [0.00000000e+00, 1.21705719e+03, 5.96848905e+02],
                          [0.00000000e+00, 0.00000000e+00, 1.00000000e+00]])
dist_coefs = np.array([0.31023497,  -0.85327455, 0.00187689, 0.0294063, 0.71110178])

i = 0

#for img_found in img_names_undistort:
while i < len(img_names_undistort):
    img = cv2.imread(img_names_undistort[i])

    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB)

    h,  w = img.shape[:2]
    newcameramtx, roi = cv2.getOptimalNewCameraMatrix(camera_matrix, dist_coefs, (w, h), 1, (w, h))

    dst = cv2.undistort(img, camera_matrix, dist_coefs, None, newcameramtx)
    print(newcameramtx)

    dst = cv2.cvtColor(dst, cv2.COLOR_BGR2RGB)

    # crop and save the image
    x, y, w, h = roi
    dst = dst[y:y+h-50, x+70:x+w-20]

    name = img_names_undistort[i].split("/")
    print(name)
    name = name[2].split(".")
    name = name[0]
    full_name = new_path + name + '.jpg'

    #outfile = img_names_undistort + '_undistorte.png'
    print('Undistorted image written to: %s' % full_name)
    cv2.imwrite(full_name, dst)
    i = i + 1