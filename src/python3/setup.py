import setuptools
import datetime

version = "1.0.0"

with open("./MusicalIllusion/_version.py", "w") as f:
    f.write('__version__ = "{0}"\n'.format(version))
    f.write('__versionDate__ = "{0}"'.format(datetime.date.today().strftime("%d/%m/%Y")))

with open("README", "r") as fh:
    long_description = fh.read()


setuptools.setup(
    name="Auditory Research Suite",
    version=version,
    author="Thomas Mudway, Michael Schutz",
    packages=setuptools.find_packages(),
    description="A suite of research tools designed for investigating auditory illusions",
    long_description=long_description,
    long_description_content_type="text/markdown",
    package_data={
        "": ["*.ui"],
    },
    include_package_data=True,
    classifiers=[
        "Programming Language :: Python :: 3",
        "Operating System :: OS Independent",
    ],
    install_requires=[
        'PyQt5',
        'numpy',
        'pyqtgraph'
    ],
    python_requires='>=3.6',
)
